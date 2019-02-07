
#include <jni.h>
#include <android/log.h>
#include <fileref.h>

#include <sys/types.h>
#include <dirent.h>
#include <errno.h>
#include <vector>
#include <string>
#include <iostream>
#include <sys/stat.h>
#include <cstring>
#include <toolkit/tpropertymap.h>
#include <iomanip>
#include <mpeg/id3v2/id3v2tag.h>

#ifdef __cplusplus
extern "C" {
#endif

using namespace std;

vector<string> fileTypes = {"mp3", "3gp", "mp4", "m4a", "aac", "ts", "flac", "mid", "xmf", "mxmf", "midi", "rtttl", "rtx", "ota", "imy", "ogg", "mkv", "wav"};

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

            // If the type is unknown, stat() the file instead.
            // This is sometimes necessary when accessing NFS mounted filesystems, but could be needed in other cases well.
            if (stat(path.c_str(), &statbuf) == 0) {
                if (S_ISREG(statbuf.st_mode)) {
                    type = DT_REG;
                } else if (S_ISDIR(statbuf.st_mode)) {
                    type = DT_DIR;
                }
            } else {
//                __android_log_print(ANDROID_LOG_INFO, "stat() failed for", "%s", path.c_str());
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

JNIEXPORT jobject JNICALL
Java_com_simplecityapps_localmediaprovider_repository_LocalSongRepository_getAudioFiles(JNIEnv *env, jobject instance, jstring initialDir_) {

    const char *initialDir = env->GetStringUTFChars(initialDir_, 0);

    vector<string> paths = vector<string>();
    paths = scanDirectory(initialDir, paths);

    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "(I)V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jclass songClass = env->FindClass("com/simplecityapps/localmediaprovider/model/AudioFile");
    jmethodID songInit = env->GetMethodID(songClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIJILjava/lang/String;JJ)V");

    jobject result = env->NewObject(arrayListClass, arrayListInit, paths.size());

    jstring unknown = env->NewStringUTF("Unknown");

    jstring title = unknown;
    jstring artist = unknown;
    jstring albumArtist = unknown;
    jstring album = unknown;
    int track = 0;
    int disc = 1;
    long duration = 0;
    int year = 0;
    jstring pathStr;

    jobject song;

    int i;
    long len = paths.size();

    struct stat statbuf;

    for (i = 0; i < len; i++) {

        TagLib::FileRef fileRef(paths[i].c_str());

        if (!fileRef.isNull()) {

            pathStr = env->NewStringUTF(paths[i].c_str());

            if (fileRef.audioProperties()) {
                TagLib::AudioProperties *properties = fileRef.audioProperties();

                duration = properties->lengthInMilliseconds();
            }

            if (fileRef.tag()) {
                TagLib::Tag *tag = fileRef.tag();

                title = env->NewStringUTF(tag->title().toCString(true));

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

            stat(paths[i].c_str(), &statbuf);

            song = env->NewObject(songClass, songInit, title, albumArtist, artist, album, track, disc, duration, year, pathStr, statbuf.st_size, statbuf.st_mtime * 1000);

            env->CallBooleanMethod(result, arrayListAdd, song);
        }
    }

    env->ReleaseStringUTFChars(initialDir_, initialDir);

    return result;

}

#ifdef __cplusplus
}
#endif

