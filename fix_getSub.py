import os

test_files_dir1 = r'src\test\java\cv\igrp\platform\access_management\users\application\queries'
test_files_dir2 = r'src\test\java\cv\igrp\platform\access_management\users\application\commands'
test_files_dir3 = r'src\test\java\cv\igrp\platform\access_management\users\mapper'

for d in [test_files_dir1, test_files_dir2, test_files_dir3]:
    if not os.path.exists(d):
        continue
    for filename in os.listdir(d):
        filepath = os.path.join(d, filename)
        if not os.path.isfile(filepath):
            continue
            
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        changed = False

        replacements = {
            '"valid-sub"': '"123"',
            '"mockExternalId"': '"123"',
            '"sub-123"': '"123"',
            '"test-sub"': '"123"',
            '"valid-token-sub"': '"123"',
            '"mock-external-id"': '"123"',
            '"jwt-subject-123"': '"123"',
            '"another-sub"': '"456"',
            '"ext123"': '"123"',
            '"valid-token"': '"123"',
            '"ext-user-1"': '"1"'
        }
        
        for old, new in replacements.items():
            if old in content:
                content = content.replace(old, new)
                changed = True

        if changed:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
            print('Updated getSub mocks in:', filepath)
