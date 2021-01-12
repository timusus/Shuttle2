package au.com.simplecityapps.shuttle.imageloading.coil.mapper

import au.com.simplecityapps.shuttle.imageloading.coil.encode
import coil.map.Mapper
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class SongUrlMapper(private val preferenceManager: GeneralPreferenceManager) : Mapper<Song, String> {

    override fun handles(data: Song): Boolean {
        return !preferenceManager.artworkLocalOnly
    }

    override fun map(data: Song): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${data.albumArtist.encode()}&album=${data.album.encode()}"
    }
}