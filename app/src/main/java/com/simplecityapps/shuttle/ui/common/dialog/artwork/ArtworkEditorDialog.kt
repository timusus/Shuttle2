package com.simplecityapps.shuttle.ui.common.dialog.artwork

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.ArtworkLocation
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.WrappedArtworkModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.common.view.fadeIn
import com.simplecityapps.shuttle.ui.common.view.fadeOut
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ArtworkEditorDialog : DialogFragment() {

    @Inject
    lateinit var httpClient: OkHttpClient

    @Inject
    lateinit var moshi: Moshi


    // Lifecycle

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val model = requireArguments().getParcelable<Parcelable>(ARG_MODEL)!!

        val imageLoader = GlideImageLoader(this)

        val view = layoutInflater.inflate(R.layout.fragment_dialog_edit_artwork, null)

        val localArtworkAdapter = RecyclerAdapter(lifecycleScope)
        val localArtworkRecyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        localArtworkRecyclerView.addItemDecoration(SpacesItemDecoration(space = 2.dp, skipFirst = true))
        localArtworkRecyclerView.adapter = localArtworkAdapter

        val remoteArtworkAdapter = RecyclerAdapter(lifecycleScope)
        val remoteArtworkRecyclerView: RecyclerView = view.findViewById(R.id.recyclerView2)
        remoteArtworkRecyclerView.addItemDecoration(SpacesItemDecoration(space = 2.dp, skipFirst = true))
        remoteArtworkRecyclerView.adapter = remoteArtworkAdapter

        val remoteProgressBar: ProgressBar = view.findViewById(R.id.remoteProgressBar)
        val remoteArtworkEmptyLabel: TextView = view.findViewById(R.id.remoteArtworkEmptyLabel)
        val artworkService =
            Retrofit
                .Builder()
                .baseUrl("https://api.shuttlemusicplayer.app/")
                .client(
                    httpClient.newBuilder().authenticator { route, response ->
                        if (route?.address?.url?.host == "api.shuttlemusicplayer.app") {
                            response.request
                                .newBuilder()
                                .header("Authorization", Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"))
                                .build()
                        } else {
                            response.request
                        }
                    }.build()
                )
                .addCallAdapterFactory(NetworkResultAdapterFactory(requireContext().getSystemService()))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ArtworkService::class.java)

        lifecycleScope.launch {
            var artistName: String = "Unknown"
            var albumName: String? = null
            when (model) {
                is AlbumArtist -> {
                    artistName = model.friendlyArtistName ?: model.name ?: requireContext().getString(R.string.unknown)
                }
                is Album -> {
                    artistName = model.friendlyArtistName ?: model.albumArtist ?: requireContext().getString(R.string.unknown)
                    albumName = model.name
                }
                is Song -> {
                    artistName = model.friendlyArtistName ?: model.albumArtist ?: requireContext().getString(R.string.unknown)
                    albumName = model.album
                }
            }

            val networkResult: NetworkResult<ImageList> = when {
                albumName != null -> artworkService.getAlbumImages(artistName, albumName)
                else -> artworkService.getArtistImages(artistName)
            }

            when (networkResult) {
                is NetworkResult.Success -> {
                    networkResult.body
                    remoteArtworkAdapter.update(networkResult.body.images.map { ArtworkImageBinder(imageLoader, ArtworkType.Remote(it.provider, it.url)) })

                    if (networkResult.body.images.isEmpty()) {
                        delay(500)
                        remoteProgressBar.fadeOut {
                            remoteArtworkEmptyLabel.fadeIn()
                            remoteArtworkRecyclerView.fadeOut()
                        }
                    } else {
                        remoteProgressBar.fadeOut()
                    }
                }
                is NetworkResult.Failure -> {
                    Timber.e(networkResult.error, "Failed to retrieve artwork")
                    delay(500)
                    remoteProgressBar.fadeOut {
                        remoteArtworkEmptyLabel.fadeIn()
                        remoteArtworkRecyclerView.fadeOut()
                    }
                }
            }

            localArtworkAdapter.update(
                listOf(
                    ArtworkImageBinder(
                        imageLoader,
                        ArtworkType.Local(WrappedArtworkModel(model, ArtworkLocation.Directory))
                    ),
                    ArtworkImageBinder(
                        imageLoader,
                        ArtworkType.Local(WrappedArtworkModel(model, ArtworkLocation.Tags))
                    )
                )
            )
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Artwork")
            .setView(view)
            .setNegativeButton(getString(R.string.dialog_button_close), null)
            .setPositiveButton(getString(R.string.dialog_button_save)) { _, _ -> }
            .create()

        return dialog
    }


    // Public

    fun show(manager: FragmentManager) {
        super.show(manager, TagEditorAlertDialog.TAG)
    }


    // Static

    companion object {
        const val ARG_MODEL = "model"

        fun newInstance(model: Parcelable) = ArtworkEditorDialog().withArgs {
            putParcelable(ARG_MODEL, model)
        }
    }
}