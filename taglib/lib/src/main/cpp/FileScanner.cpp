
#include <jni.h>
#include <fileref.h>

#include <sys/types.h>
#include <dirent.h>
#include <errno.h>
#include <vector>
#include <string>
#include <iostream>
#include <sys/stat.h>
#include <cstring>
#include <tpropertymap.h>
#include <iomanip>
#include <id3v2tag.h>
#include <tfilestream.h>

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;

jclass globalSongClass;
jmethodID songInit;
jstring unknown;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass songClass = env->FindClass("com/simplecityapps/taglib/AudioFile");
    globalSongClass = reinterpret_cast<jclass>(env->NewGlobalRef(songClass));
    env->DeleteLocalRef(songClass);

    songInit = env->GetMethodID(globalSongClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;JJ)V");

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);

    env->DeleteGlobalRef(globalSongClass);
}

vector<string> fileTypes = {"mp3", "3gp", "mp4", "m4a", "m4b", "aac", "ts", "flac", "mid", "xmf", "mxmf", "midi", "rtttl", "rtx", "ota", "imy", "ogg", "mkv", "wav"};

string getExtension(const char *file) {
    size_t len = strlen(file);

    const char *p = file + len;

    for (; p > file && *p != '.' && *p != '\\'; --p) {}

    if (*p == '.')
        return p + 1;

    return '.' + file + len;
}

vector<string> &scanDirectory(const string &path, vector<string> &files) {

    DIR *dir;

    struct dirent *entry;

    if ((dir = opendir(path.c_str())) == NULL) {
        cout << "Error(" << errno << ") opening " << path << endl;
        return files;
    }

    while ((entry = readdir(dir)) != NULL) {

        const char *name = entry->d_name;

        // Skip '.' and '..'
        if (name[0] == '.' && (name[1] == 0 || (name[1] == '.' && name[2] == 0))) {
            continue;
        }

        struct stat statbuf;

        int type = entry->d_type;

        if (type == DT_UNKNOWN) {

            // If the type is unknown, stat() the file instead. This is sometimes necessary when accessing NFS mounted filesystems, but could be needed in other cases well.
            if (stat(path.c_str(), &statbuf) == 0) {
                if (S_ISREG(statbuf.st_mode)) {
                    type = DT_REG;
                } else if (S_ISDIR(statbuf.st_mode)) {
                    type = DT_DIR;
                }
            }
        }

        if (type == DT_DIR) {
            scanDirectory(path + "/" + name, files);

        } else if (type == DT_REG) {

            string extension = getExtension(name);
            if (!extension.empty()) {
                for (const auto &e : fileTypes) {
                    if (extension.find(e) != std::string::npos) {
                        files.push_back(path + "/" + name);
                    }
                }
            }
        }
    }
    closedir(dir);

    return files;
}

JNIEXPORT jobject
JNICALL
Java_com_simplecityapps_taglib_FileScanner_getAudioFile(JNIEnv *env, jobject instance, jstring pathStr, jint fd_, jstring fileName) {

    int fd = (int) fd_;

    unknown = env->NewStringUTF("Unknown");

    jstring title = fileName;
    jstring artist = unknown;
    jstring albumArtist = unknown;
    jstring album = unknown;
    int track = 0;
    int disc = 1;
    int duration = 0;
    int year = 0;

    struct stat statbuf;

    TagLib::IOStream *stream = new TagLib::FileStream(fd, true);
    TagLib::FileRef fileRef(stream);

    if (!fileRef.isNull()) {

        if (fileRef.audioProperties()) {
            TagLib::AudioProperties *properties = fileRef.audioProperties();

            duration = properties->lengthInMilliseconds();
        }

        if (fileRef.tag()) {
            TagLib::Tag *tag = fileRef.tag();

            if (tag->title().isEmpty()) {
                title = fileName;
            } else {
                title = env->NewStringUTF(tag->title().toCString(true));
            }

            if (tag->artist().isEmpty()) {
                artist = unknown;
            } else {
                artist = env->NewStringUTF(tag->artist().toCString(true));
            }

            const TagLib::PropertyMap &properties = tag->properties();

            albumArtist = artist;
            if (properties.contains("ALBUMARTIST")) {
                const TagLib::StringList &stringList = properties["ALBUMARTIST"];
                if (!stringList.isEmpty()) {
                    albumArtist = env->NewStringUTF(stringList.front().toCString(true));
                }
            }

            if (tag->album().isEmpty()) {
                album = unknown;
            } else {
                album = env->NewStringUTF(tag->album().toCString(true));
            }

            track = tag->track();

            if (properties.contains("DISCNUMBER")) {
                const TagLib::StringList &stringList = properties["DISCNUMBER"];
                if (!stringList.isEmpty()) {
                    disc = stringList.front().toInt();
                    if (disc <= 0) {
                        disc = 1;
                    }
                }
            }

            year = tag->year();
        }

        fstat(fd, &statbuf);

        jobject song = env->NewObject(globalSongClass, songInit, title, albumArtist, artist, album, track, disc, duration, year, pathStr, statbuf.st_size, statbuf.st_mtime * 1000);
//        env->DeleteLocalRef(title);
//        env->DeleteLocalRef(artist);
//        env->DeleteLocalRef(albumArtist);
//        env->DeleteLocalRef(album);

        return song;
    }

    return nullptr;
}

#ifdef __cplusplus
}
#endif

