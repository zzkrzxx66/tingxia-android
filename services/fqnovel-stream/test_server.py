import importlib.util
import io
import pathlib
import tempfile
import threading
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('stream_server', pathlib.Path(__file__).with_name('server.py'))
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


class StreamServerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.cache = pathlib.Path(self.temp.name)
        self.patches = [patch.object(server, 'CACHE_DIR', self.cache), patch.object(server, 'MAX_CACHE_BYTES', 5)]
        for item in self.patches:
            item.start()

    def tearDown(self):
        for item in reversed(self.patches):
            item.stop()
        self.temp.cleanup()

    def test_cache_probe_racing_with_cleanup_is_a_miss(self):
        with patch.object(pathlib.Path, 'is_file', return_value=True), \
                patch.object(pathlib.Path, 'stat', side_effect=FileNotFoundError):
            self.assertIsNone(server.cached_path('1', '3', '2'))

    def test_range_boundaries(self):
        for header in ('bytes=-', 'bytes=-0', 'bytes=10-', 'bytes=5-4', 'bytes=0-1,3-4'):
            with self.subTest(header=header), self.assertRaises(ValueError):
                server.parse_range(header, 10)
        self.assertEqual((0, 9), server.parse_range(None, 10))
        self.assertEqual((7, 9), server.parse_range('bytes=-3', 10))
        self.assertEqual((2, 9), server.parse_range('bytes=2-99', 10))
        self.assertEqual((0, 9), server.parse_range('bytes=-99', 10))

    def test_oversized_generated_file_survives_return_and_locks_are_retired(self):
        def normalize(command, **kwargs):
            pathlib.Path(command[-1]).write_bytes(b'0123456789')
        with patch.object(server, 'json_request', return_value={'data': {'main_url': 'mock', 'encryption_key': 'mock'}}), \
                patch.object(server, 'decode_spade_a', return_value=b'0' * 16), \
                patch.object(server, 'download'), patch.object(server, 'probe_codec', return_value='aac'), \
                patch.object(server.subprocess, 'run', side_effect=normalize):
            path = server.prepare_chapter('1', '3', '2')
        self.assertEqual(b'0123456789', path.read_bytes())
        self.assertEqual({}, server.locks)
        server.enforce_cache_budget()
        self.assertFalse(path.exists())

    def test_active_chapter_survives_concurrent_housekeeping(self):
        active = self.cache / '1_2_3.m4a'
        disposable = self.cache / '4_5_6.m4a'
        active.write_bytes(b'0123456789')
        disposable.write_bytes(b'0123456789')
        with server.chapter_lease('1/2/3'):
            worker = threading.Thread(target=server.enforce_cache_budget)
            worker.start()
            worker.join(timeout=2)
            self.assertFalse(worker.is_alive())
            self.assertTrue(active.exists())
            self.assertFalse(disposable.exists())
        server.enforce_cache_budget()
        self.assertFalse(active.exists())
        self.assertEqual({}, server.locks)

    def test_file_is_open_before_headers_and_remains_readable_after_unlink(self):
        path = self.cache / '1_2_3.m4a'
        path.write_bytes(b'0123456789')
        handler = object.__new__(server.Handler)
        handler.headers = {'Range': 'bytes=2-5'}
        handler.wfile = io.BytesIO()
        handler.send_response = lambda status: path.unlink()
        handler.send_header = lambda *args: None
        handler.end_headers = lambda: None
        handler.serve_file(path, True)
        self.assertEqual(b'2345', handler.wfile.getvalue())

    def test_empty_range_returns_416_without_body(self):
        path = self.cache / '1_2_3.m4a'
        path.write_bytes(b'0123456789')
        handler = object.__new__(server.Handler)
        handler.headers = {'Range': 'bytes=-'}
        handler.wfile = io.BytesIO()
        statuses, headers = [], {}
        handler.send_response = statuses.append
        handler.send_header = lambda key, value: headers.update({key: value})
        handler.end_headers = lambda: None
        handler.serve_file(path, True)
        self.assertEqual([416], statuses)
        self.assertEqual('bytes */10', headers['Content-Range'])
        self.assertEqual(b'', handler.wfile.getvalue())

    def test_warm_queue_is_bounded(self):
        with patch.object(server, 'warming', {'other'}), patch.object(server, 'MAX_WARM_QUEUE', 1), \
                patch.object(server.warm_pool, 'submit') as submit:
            self.assertEqual('busy', server.warm_chapter('1', '3', '2'))
            submit.assert_not_called()

    def test_same_chapter_is_prepared_once_for_concurrent_requests(self):
        entered, release = threading.Event(), threading.Event()
        outputs, failures = [], []
        def download(*args):
            entered.set()
            self.assertTrue(release.wait(2))
        def normalize(command, **kwargs):
            pathlib.Path(command[-1]).write_bytes(b'audio')
        def prepare():
            try:
                outputs.append(server.prepare_chapter('1', '3', '2'))
            except Exception as exc:
                failures.append(exc)
        with patch.object(server, 'json_request', return_value={'data': {'main_url': 'mock', 'encryption_key': 'mock'}}), \
                patch.object(server, 'decode_spade_a', return_value=b'0' * 16), \
                patch.object(server, 'download', side_effect=download) as fetch, \
                patch.object(server, 'probe_codec', return_value='aac'), patch.object(server.subprocess, 'run', side_effect=normalize):
            workers = [threading.Thread(target=prepare) for _ in range(2)]
            workers[0].start()
            self.assertTrue(entered.wait(2))
            workers[1].start()
            release.set()
            for worker in workers:
                worker.join(2)
                self.assertFalse(worker.is_alive())
            self.assertEqual([], failures)
            self.assertEqual(1, fetch.call_count)
            self.assertEqual(2, len(outputs))
            self.assertEqual(outputs[0], outputs[1])


if __name__ == '__main__':
    unittest.main()
