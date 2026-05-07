import os
import re

directories = [
    r'src\test\java\cv\igrp\platform\access_management\users\application\queries',
    r'src\test\java\cv\igrp\platform\access_management\users\application\commands'
]

for d in directories:
    if not os.path.exists(d):
        continue
    for filename in os.listdir(d):
        filepath = os.path.join(d, filename)
        if not os.path.isfile(filepath):
            continue
            
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        changed = False

        if 'findByExternalIdWithRolesAndPermissions' in content:
            content = re.sub(
                r'findByExternalIdWithRolesAndPermissions\([^)]+\)',
                r'findByIdWithRolesAndPermissions(anyInt())',
                content
            )
            changed = True

        if 'findByExternalId(' in content:
            content = re.sub(
                r'findByExternalId\([^)]+\)',
                r'findById(anyInt())',
                content
            )
            changed = True

        if changed:
            if 'import static org.mockito.ArgumentMatchers.anyInt;' not in content:
                content = content.replace('import org.mockito.Mockito;', 'import org.mockito.Mockito;\nimport static org.mockito.ArgumentMatchers.anyInt;')
                content = content.replace('import static org.mockito.ArgumentMatchers.any;', 'import static org.mockito.ArgumentMatchers.any;\nimport static org.mockito.ArgumentMatchers.anyInt;')
                if 'import static org.mockito.ArgumentMatchers.anyInt;' not in content:
                     content = 'import static org.mockito.ArgumentMatchers.anyInt;\n' + content

            content = content.replace('"sub123"', '"123"')
            content = content.replace('"sub999"', '"999"')
            content = content.replace('"sub111"', '"111"')
            content = content.replace('"missing1122"', '"1122"')
            content = content.replace('"abc"', '"1"')
            content = content.replace('"sub888"', '"888"')
            content = content.replace('"missing"', '"2"')
            content = content.replace('"user-ext-1"', '"1"')
            content = content.replace('"unknown-ext"', '"2"')
            content = content.replace('"ext-user-1"', '"1"')
            content = content.replace('"unknown-user"', '"2"')
            content = content.replace('"mock-external-id"', '"123"')
            content = content.replace('"mockExternalId"', '"123"')
            
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            print('Updated:', filepath)
