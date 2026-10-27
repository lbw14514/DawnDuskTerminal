"""Minimal NBT reader/writer (big-endian), gzip aware."""
import gzip
import io
import struct

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class Tag:
    __slots__ = ("id", "value")

    def __init__(self, tag_id, value):
        self.id = tag_id
        self.value = value

    def __repr__(self):
        return f"Tag({self.id}, {self.value!r})"


class _Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, n):
        v = self.d[self.i:self.i + n]
        if len(v) != n:
            raise EOFError("truncated")
        self.i += n
        return v

    def u1(self):
        return self.take(1)[0]

    def i1(self):
        return struct.unpack(">b", self.take(1))[0]

    def i2(self):
        return struct.unpack(">h", self.take(2))[0]

    def u2(self):
        return struct.unpack(">H", self.take(2))[0]

    def i4(self):
        return struct.unpack(">i", self.take(4))[0]

    def i8(self):
        return struct.unpack(">q", self.take(8))[0]

    def f4(self):
        return struct.unpack(">f", self.take(4))[0]

    def f8(self):
        return struct.unpack(">d", self.take(8))[0]

    def string(self):
        n = self.u2()
        return self.take(n).decode("utf-8")


def _read_payload(r, tag_id):
    if tag_id == TAG_BYTE:
        return r.i1()
    if tag_id == TAG_SHORT:
        return r.i2()
    if tag_id == TAG_INT:
        return r.i4()
    if tag_id == TAG_LONG:
        return r.i8()
    if tag_id == TAG_FLOAT:
        return r.f4()
    if tag_id == TAG_DOUBLE:
        return r.f8()
    if tag_id == TAG_BYTE_ARRAY:
        n = r.i4()
        return list(r.take(n))
    if tag_id == TAG_STRING:
        return r.string()
    if tag_id == TAG_LIST:
        elem = r.u1()
        n = r.i4()
        return [_read_payload(r, elem) for _ in range(n)]
    if tag_id == TAG_COMPOUND:
        out = {}
        while True:
            t = r.u1()
            if t == TAG_END:
                return out
            nm = r.string()
            out[nm] = Tag(t, _read_payload(r, t))
    if tag_id == TAG_INT_ARRAY:
        n = r.i4()
        return list(struct.unpack(">%di" % n, r.take(4 * n)))
    if tag_id == TAG_LONG_ARRAY:
        n = r.i4()
        return list(struct.unpack(">%dq" % n, r.take(8 * n)))
    raise ValueError("unknown tag id %d at %d" % (tag_id, r.i))


def load(path):
    with open(path, "rb") as f:
        raw = f.read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    r = _Reader(raw)
    root_type = r.u1()
    if root_type != TAG_COMPOUND:
        raise ValueError("root is not a compound")
    root_name = r.string()
    return root_name, Tag(TAG_COMPOUND, _read_payload(r, TAG_COMPOUND)), len(raw)


# ---------------------------------------------------------------- writing

def _w_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _w_payload(tag_id, value):
    if tag_id == TAG_BYTE:
        return struct.pack(">b", value)
    if tag_id == TAG_SHORT:
        return struct.pack(">h", value)
    if tag_id == TAG_INT:
        return struct.pack(">i", value)
    if tag_id == TAG_LONG:
        return struct.pack(">q", value)
    if tag_id == TAG_FLOAT:
        return struct.pack(">f", value)
    if tag_id == TAG_DOUBLE:
        return struct.pack(">d", value)
    if tag_id == TAG_BYTE_ARRAY:
        return struct.pack(">i", len(value)) + bytes((v & 0xFF) for v in value)
    if tag_id == TAG_STRING:
        return _w_string(value)
    if tag_id == TAG_LIST:
        if not value:
            return struct.pack(">bi", TAG_END, 0)
        elem_id = _infer_list_id(value)
        body = b"".join(_w_payload(elem_id, v) for v in value)
        return struct.pack(">bi", elem_id, len(value)) + body
    if tag_id == TAG_COMPOUND:
        out = b""
        for name, tag in value.items():
            out += struct.pack(">b", tag.id) + _w_string(name) + _w_payload(tag.id, tag.value)
        return out + struct.pack(">b", TAG_END)
    if tag_id == TAG_INT_ARRAY:
        return struct.pack(">i", len(value)) + struct.pack(">%di" % len(value), *value)
    if tag_id == TAG_LONG_ARRAY:
        return struct.pack(">i", len(value)) + struct.pack(">%dq" % len(value), *value)
    raise ValueError("bad tag %d" % tag_id)


def _infer_list_id(value):
    v = value[0]
    if isinstance(v, bool) or isinstance(v, int):
        return TAG_INT
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, Tag):
        return v.id
    if isinstance(v, dict):
        return TAG_COMPOUND
    raise ValueError("cannot infer list element type for %r" % (v,))


def save(path, root_name, root_compound_tag, compress=True):
    body = struct.pack(">b", TAG_COMPOUND) + _w_string(root_name)
    body += _w_payload(TAG_COMPOUND, root_compound_tag.value)
    if compress:
        buf = io.BytesIO()
        with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
            gz.write(body)
        body = buf.getvalue()
    with open(path, "wb") as f:
        f.write(body)
