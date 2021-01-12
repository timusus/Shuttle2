package au.com.simplecityapps.shuttle.imageloading.coil.mapper

import au.com.simplecityapps.shuttle.imageloading.coil.encode
import coil.map.Mapper
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class AlbumUrlMapper(private val preferenceManager: GeneralPreferenceManager) : Mapper<Album, String> {

    override fun handles(data: Album): Boolean {
        return !preferenceManager.artworkLocalOnly
    }

    override fun map(data: Album): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${data.albumArtist.encode()}&album=${data.name.encode()}"
    }
}