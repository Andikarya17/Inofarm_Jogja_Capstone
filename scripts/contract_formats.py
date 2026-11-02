"""Inofarm timestamp checks without optional third-party date-time format packages."""
import re
from datetime import datetime
from jsonschema import FormatChecker

wire_formats = FormatChecker()


@wire_formats.checks('date-time', raises=ValueError)
def timestamp(value):
    if not isinstance(value, str):
        return True  # Type is checked by its schema.
    if not re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.[0-9]{1,9})?(?:Z|[+-]\d{2}:\d{2})', value):
        return False
    return datetime.fromisoformat(value.replace('Z', '+00:00')).utcoffset() is not None
