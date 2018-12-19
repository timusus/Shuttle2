package com.simplecityapps.shuttle.ui.screens.library.folders

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.utils.findParent
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import dagger.android.support.AndroidSupportInjection
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import javax.inject.Inject


class FolderDetailFragment : Fragment(), FolderBinder.Listener {

    private val compositeDisposable = CompositeDisposable()

    private val adapter = SectionedAdapter()

    private lateinit var path: String

    private lateinit var model: FolderViewModel

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        path = FolderDetailFragmentArgs.fromBundle(arguments).path
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findParent(FolderFragment::class.java)?.let { folderFragment ->
            model = ViewModelProviders.of(folderFragment, viewModelFactory).get(FolderViewModel::class.java)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(com.simplecityapps.shuttle.R.layout.fragment_folder_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findToolbarHost()?.getToolbar()?.let { toolbar ->
            toolbar.subtitle = path
        }

        recyclerView.adapter = adapter

        compositeDisposable.add(model.getRoot()
            .map { root ->
                root.find(path)?.children
                    ?.map {
                        val binder = FolderBinder(it.node)
                        binder.listener = this
                        binder
                    }
                    ?: emptyList()
            }
            .subscribe { viewBinders ->
                adapter.setData(viewBinders)
            })
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }


    // FolderBinder.Listener Implementation

    override fun onNodeSelected(node: Node<Song>) {
        when (node.data) {
            null -> view?.findNavController()?.navigate(
                R.id.action_folderDetailFragment_self,
                FolderDetailFragmentArgs.Builder()
                    .setPath(node.path)
                    .build()
                    .toBundle()
            )
        }
    }


    // Static

    companion object {

        const val TAG = "FolderDetailFragment"

    }
}