pkgs=''
pkgs+='res/raw/app_overlay_1 '
pkgs+='res/raw/app_overlay_2 '
pkgs+='res/raw/some_other_app_overlay '
pkgs+='res/raw/system_overlay_1 '
pkgs+='res/raw/system_overlay_2 '

for pkg in $pkgs; do
    adb push $pkg /vendor/overlay/$(basename $pkg).apk
done
