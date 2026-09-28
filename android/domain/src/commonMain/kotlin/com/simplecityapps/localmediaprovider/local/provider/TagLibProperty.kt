package com.simplecityapps.localmediaprovider.local.provider

enum class TagLibProperty(val key: String) {
    Title("TITLE"),
    Artist("ARTIST"),
    Album("ALBUM"),
    AlbumArtist("ALBUMARTIST"),
    Date("DATE"),
    Track("TRACKNUMBER"),
    Disc("DISCNUMBER"),
    Genre("GENRE"),
    OriginalDate("ORIGINALDATE"),
    Year("YEAR"),
    ReplayGainTrack("REPLAYGAIN_TRACK_GAIN"),
    ReplayGainAlbum("REPLAYGAIN_ALBUM_GAIN"),
    Lyrics("LYRICS"),
    Grouping("GROUPING"),

    // TagLib's unified names, whatever the container: ARTISTS and ALBUMARTISTS are Vorbis comments, ID3 TXXX frames or
    // MP4 freeform atoms of that name; COMPILATION is also ID3 TCMP and MP4 cpil. The MusicBrainz ids are Vorbis
    // comments, ID3 TXXX "MusicBrainz ... Id" frames (the recording id is the UFID frame) and MP4 freeform
    // "----:com.apple.iTunes:MusicBrainz ... Id" atoms.
    Artists("ARTISTS"),
    AlbumArtists("ALBUMARTISTS"),
    Compilation("COMPILATION"),
    MusicBrainzTrackId("MUSICBRAINZ_TRACKID"),
    MusicBrainzAlbumId("MUSICBRAINZ_ALBUMID"),
    MusicBrainzReleaseGroupId("MUSICBRAINZ_RELEASEGROUPID"),
    MusicBrainzArtistId("MUSICBRAINZ_ARTISTID"),
    MusicBrainzAlbumArtistId("MUSICBRAINZ_ALBUMARTISTID")
}
