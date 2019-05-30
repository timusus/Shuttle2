#include <jni.h>
#include <fileref.h>
#include <tpicturemap.h>
#include "tag.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jbyteArray JNICALL
Java_com_simplecityapps_taglib_ArtworkProvider_getArtwork(JNIEnv *env, jobject instance, jstring path_) {

    const char *path = env->GetStringUTFChars(path_, 0);
    TagLib::FileRef fileRef(path);

    if (!fileRef.isNull()) {
        TagLib::Tag *tag = fileRef.tag();

        TagLib::PictureMap pictureMap = tag->pictures();

        TagLib::Picture picture;

        // Finds the largest picture by byte size
        size_t picSize = 0;
        for (auto const &x: pictureMap) {
            for (auto const &y: x.second) {
                size_t size = y.data().size();
                if (size > picSize) {
                    picture = y;
                }
            }
        };

        TagLib::ByteVector byteVector = picture.data();
        size_t len = byteVector.size();
        if (len > 0) {
            jbyteArray arr = env->NewByteArray(len);
            char *data = byteVector.data();
            env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(data));
            return arr;
        }
    }

    return nullptr;
}


#ifdef __cplusplus
}
#endif

