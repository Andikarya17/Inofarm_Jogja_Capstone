"""Regenerate profile-only shared test ABI roots from the pinned cached test binaries.

Run from repository root with Python 3. No downloads or production release changes.
Review the generated diff and rerun profile instrumentation after dependency changes.
The supplementary hand-reviewed profile rules cover app fixtures and MockWebServer.
"""
from pathlib import Path
from collections import defaultdict
import io
import os
import struct
import zipfile

CACHE = Path('.tools/gradle-home/caches/modules-2/files-2.1')
PATTERNS = (
    'androidx.compose.ui/ui-test*-android/1.9.0/*/*.aar',
    'org.jetbrains.kotlinx/kotlinx-coroutines-test-jvm/1.8.1/*/*.jar',
    'androidx.test.espresso/espresso-core/3.7.0/*/*.aar',
)
members = defaultdict(set)
bases = set()


def shared(name):
    return name.startswith(('androidx.compose.', 'kotlin.', 'kotlinx.coroutines.',
                            'androidx.lifecycle.', 'androidx.activity.', 'androidx.savedstate.',
                            'dev.chrisbanes.haze.', 'okhttp3.', 'okio.',
                            'androidx.core.', 'androidx.concurrent.', 'com.google.common.')) \
        and '.test.' not in name and not name.endswith('.test')


def java_type(descriptor, index=0):
    dimensions = 0
    while descriptor[index] == '[':
        dimensions += 1
        index += 1
    if descriptor[index] == 'L':
        end = descriptor.index(';', index)
        name = descriptor[index + 1:end].replace('/', '.')
        index = end + 1
    else:
        name = dict(V='void', I='int', J='long', Z='boolean', B='byte', C='char',
                    S='short', D='double', F='float')[descriptor[index]]
        index += 1
    return name + '[]' * dimensions, index


def signature(name, descriptor):
    if not descriptor.startswith('('):
        return java_type(descriptor)[0] + ' ' + name + ';'
    index, arguments = 1, []
    while descriptor[index] != ')':
        argument, index = java_type(descriptor, index)
        arguments.append(argument)
    result = '' if name == '<init>' else java_type(descriptor, index + 1)[0] + ' '
    return result + name + '(' + ','.join(arguments) + ');'


def inspect_class(data):
    position = 8

    def u2():
        nonlocal position
        value = struct.unpack_from('>H', data, position)[0]
        position += 2
        return value

    pool = [None] * u2()
    index = 1
    while index < len(pool):
        tag = data[position]
        position += 1
        if tag == 1:
            length = u2()
            pool[index] = (tag, data[position:position + length].decode('utf-8', errors='replace'))
            position += length
        elif tag in (3, 4):
            position += 4
        elif tag in (5, 6):
            position += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, u2(), u2())
        elif tag == 15:
            position += 3
        else:
            raise ValueError(f'Unsupported constant pool tag {tag}')
        index += 1

    def class_name(entry):
        return pool[pool[entry][1]][1].replace('/', '.')

    u2()  # access flags
    u2()  # current class
    superclass = u2()
    if superclass and shared(class_name(superclass)):
        bases.add(class_name(superclass))
    for _ in range(u2()):
        interface = class_name(u2())
        if shared(interface):
            bases.add(interface)
    for item in pool:
        if not item:
            continue
        if item[0] == 7:
            name = pool[item[1]][1].replace('/', '.')
            if shared(name):
                members[name]
        elif item[0] in (9, 10, 11):
            owner = class_name(item[1])
            if shared(owner):
                pair = pool[item[2]]
                members[owner].add(signature(pool[pair[1]][1], pool[pair[2]][1]))


archives = [path for pattern in PATTERNS for path in CACHE.glob(pattern)]
if len(archives) != 4:
    raise SystemExit('Expected two Compose test AARs, Espresso core AAR and one coroutine test JAR in cache.')
for path in archives:
    with zipfile.ZipFile(path) as archive:
        content = zipfile.ZipFile(io.BytesIO(archive.read('classes.jar'))) if path.suffix == '.aar' else archive
        try:
            for name in content.namelist():
                if name.endswith('.class'):
                    inspect_class(content.read(name))
        finally:
            if content is not archive:
                content.close()

compiled_tests = Path('android/app/build/intermediates/built_in_kotlinc/profileAndroidTest/compileProfileAndroidTestKotlin/classes')
if not compiled_tests.is_dir():
    raise SystemExit('Compile profileAndroidTest Kotlin before generating ABI roots.')
for path in compiled_tests.rglob('*.class'):
    readable = Path('\\\\?\\' + str(path.resolve())) if os.name == 'nt' else path
    inspect_class(readable.read_bytes())

lines = [
    '# Profile only. Exact shared ABI referenced by Compose UI test 1.9.0 and',
    '# coroutines-test 1.8.1, Espresso 3.7.0 and app tests; no package keep-all.',
    '# Release excludes this file. External callers/subclasses require ABI stability.',
    '# Generated by scripts/generate-profile-test-abi.py; review after test dependency changes.',
]
for owner, references in sorted(members.items()):
    lines.append('-keep class ' + owner + ' {')
    lines.extend('    ' + member for member in sorted(references))
    if owner in bases:
        lines.append('    public *; protected *;')
    lines.append('}')
destination = Path('android/app/proguard-profile-test-abi.pro')
destination.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'{len(members)} classes, {sum(map(len, members.values()))} exact members, {len(bases)} external bases')
