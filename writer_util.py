import io
import json
import os
import sys

def write_template(template, namespace):
    for key, value in namespace.items():
        template = template.replace(f'#{key}#', value)
    return template

def transform_template(in_tmp, out_tmp, namespace):
    with open(in_tmp) as file:
        template = file.read()
    content = write_template(template, namespace)
    with open(out_tmp, 'w') as file:
        file.write(content)

def invoke(cmd, in_file, out_file):
    os.system(f'java -jar {cmd} {in_file} {out_file}')

def apply(cmd, in_tmp, path_tmp, namespaces_path, loops=1):
    with open(namespaces_path) as file:
        namespaces = json.load(file)
    for namespace in namespaces:
        filename = write_template(path_tmp, namespace)
        transform_template(in_tmp, 'temp.json', namespace)
        invoke(cmd, 'temp.json', filename)
        if loops > 1:
            loop(filename, 'looped.wav', loops)
            os.remove(filename)
            os.rename('looped.wav', filename)

def loop(in_dir, out_dir, times):
    with open(in_dir, 'rb') as in_file, open(out_dir, 'wb') as out_file:
        # Copy Header
        bytes = in_file.read(44)
        out_file.write(bytes)
        # Get input data length
        in_file.seek(0, io.SEEK_END)
        data_size = in_file.tell() - 44
        print(f'{data_size=}, {times=}')
        # Copy data enough times
        for _ in range(times):
            in_file.seek(44, io.SEEK_SET)
            for i in range(0, data_size, 4096):
                num_bytes = min(4096, data_size - i)
                bytes = in_file.read(num_bytes)
                out_file.write(bytes)
        # Write new file and data length
        out_file.flush()
        out_file.seek(4, io.SEEK_SET)
        out_file.write((36 + data_size * times).to_bytes(4, 'little'))
        out_file.flush()
        out_file.seek(40, io.SEEK_SET)
        out_file.write((data_size * times).to_bytes(4, 'little'))
        out_file.flush()

def main():
    _, jar, in_tmp, path_tmp, namespaces_path = sys.argv[:5]
    if len(sys.argv) >= 6:
        loops = int(sys.argv[5])
    else:
        loops = 1
    apply(jar, in_tmp, path_tmp, namespaces_path, loops)

if __name__ == '__main__':
    main()
