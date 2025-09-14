import json
import os
import sys

def write_template(template, namespace):
    for key, value in namespace.items():
        template = template.replace(f'#{key}#', value)
    return template

def transform_template(in_tmp, out_tmp, namespace):
    with open(in_tmp) as file:
        template = in_tmp.read()
    content = write_template(template, namespace)
    with open(out_tmp, 'w') as file:
        file.write(content)

def invoke(cmd, in_file, out_file):
    os.system(f'java -jar {cmd} {in_file} {out_file}')

def apply(cmd, in_tmp, path_tmp, namespaces_path):
    namespaces = json.load(namespaces_path)
    for namespace in namespaces:
        filename = write_template(path_tmp, namespace)
        transform_template(in_tmp, 'temp.json', namespace)
        invoke(cmd, 'temp.json', filename)

def main():
    _, jar, in_tmp, path_tmp, namespaces_path = sys.argv
    apply(jar, in_tmp, path_tmp, namespaces_path)

if __name__ == '__main__':
    main()