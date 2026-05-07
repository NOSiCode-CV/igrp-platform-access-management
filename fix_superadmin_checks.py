import os

files_to_update = [
    r'src\main\java\cv\igrp\platform\access_management\users\application\queries\GetCurrentUserApplicationMenusQueryHandler.java',
    r'src\main\java\cv\igrp\platform\access_management\users\application\queries\GetCurrentUserDepartmentsQueryHandler.java',
    r'src\main\java\cv\igrp\platform\access_management\users\application\queries\GetCurrentUserDepartmentRolesQueryHandler.java',
    r'src\main\java\cv\igrp\platform\access_management\users\application\queries\GetCurrentUserApplicationsQueryHandler.java'
]

for filepath in files_to_update:
    if os.path.isfile(filepath):
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        content = content.replace(
            'user.getExternalId().equals(SUPER_ADMIN_EXTERNAL_ID)',
            'SUPER_ADMIN_EXTERNAL_ID.equals(user.getUsername())'
        )

        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print('Updated SUPER_ADMIN check in:', filepath)
