package com.simplecityapps.saf

import android.net.Uri
import java.io.Serializable

interface FileNode : Node, Serializable {
    val uri: Uri
    val displayName: String
}
