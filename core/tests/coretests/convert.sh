sed -i -e's/FrameworkCoreTests_BUILD_PACKAGE/BUILD_PACKAGE/' */Android.mk
for i in */Android.mk; do androidmk $i > ${i/.mk/.bp}; done
sed -i -e's/\(name:.*\)$/\0\n    defaults: ["FrameworksCoreTests_defaults"],/' */Android.bp
sed -i -e's/name: "/name: "FrameworksCoreTests_/' */Android.bp
sed -i -e's/LOCAL_PATH + "\/..\/..\/certs\/\(.*\)"/":FrameworksCoreTests_\1_cert"/' */Android.bp
sed -i -e'/--version-/ {N; s/",\n        "/ /}' */Android.bp
sed -i -e's/android_test/android_test_helper_app/' */Android.bp
sed -i -e's/android_app/android_test_helper_app/' */Android.bp
git checkout HEAD install_jni_lib/Android.bp
