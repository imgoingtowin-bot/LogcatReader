package com.dp.logcatapp.fragments.filters

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import com.dp.logcat.LogPriority
import com.dp.logcatapp.R
import com.dp.logcatapp.db.FiltersDB
import com.dp.logcatapp.db.LogcatFilterRow
import com.dp.logcatapp.fragments.base.BaseFragment
import com.dp.logcatapp.fragments.filters.dialogs.FilterDialogFragment
import com.dp.logcatapp.fragments.logcatlive.LogcatLiveViewModel
import com.dp.logcatapp.util.inflateLayout
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class FiltersFragment : BaseFragment() {

    companion object {
        val TAG = FiltersFragment::class.qualifiedName
    }

    private lateinit var viewModel: LogcatLiveViewModel
    private lateinit var recyclerViewAdapter: MyRecyclerViewAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(activity!!)
                .get(LogcatLiveViewModel::class.java)
        recyclerViewAdapter = MyRecyclerViewAdapter({
            onRemoveClicked(it)
        })

        FiltersDB.getInstance(activity!!)
                .filterDAO()
                .getAll()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val data = it.map {
                        val logPriorities = it.logPriorities.split(",")
                                .joinToString(", ") {
                                    when (it) {
                                        LogPriority.ASSERT -> "Assert"
                                        LogPriority.ERROR -> "Error"
                                        LogPriority.DEBUG -> "Debug"
                                        LogPriority.FATAL -> "Fatal"
                                        LogPriority.INFO -> "Info"
                                        LogPriority.VERBOSE -> "Verbose"
                                        LogPriority.WARNING -> "warning"
                                        else -> ""
                                    }
                                }
                        FilterListItem(it.keyword, logPriorities, it)
                    }
                    recyclerViewAdapter.setData(data)
                }
    }

    private fun onRemoveClicked(v: View) {
        val pos = linearLayoutManager.getPosition(v)
        if (pos != RecyclerView.NO_POSITION) {
            val item = recyclerViewAdapter[pos]
            recyclerViewAdapter.remove(pos)
            Flowable.just(FiltersDB.getInstance(context!!))
                    .subscribeOn(Schedulers.io())
                    .subscribe {
                        it.filterDAO().delete(item.filter!!)
                    }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflateLayout(R.layout.filters_fragment)

        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.addItemDecoration(DividerItemDecoration(activity!!,
                DividerItemDecoration.VERTICAL))
        linearLayoutManager = LinearLayoutManager(activity!!)
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.adapter = recyclerViewAdapter

        return rootView
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.filters, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add_action -> {
                val frag = FilterDialogFragment()
                frag.setTargetFragment(this, 0)
                frag.show(fragmentManager, FilterDialogFragment.TAG)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun addFilter(keyword: String, logLevels: Set<String>) {
        if (keyword.isEmpty() && logLevels.isEmpty()) {
            return
        }

        Flowable.just(FiltersDB.getInstance(context!!))
                .subscribeOn(Schedulers.io())
                .subscribe {
                    it.filterDAO().insert(LogcatFilterRow(keyword,
                            logLevels.sorted().joinToString(","), ""))
                }
    }
}

internal data class FilterListItem(val keyword: String,
                                   val logLevelsStr: String,
                                   val filter: LogcatFilterRow? = null)

internal class MyRecyclerViewAdapter(private val onRemoveListener: (View) -> Unit) :
        RecyclerView.Adapter<MyRecyclerViewAdapter.MyViewHolder>() {

    private val data = mutableListOf<FilterListItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = parent.context.inflateLayout(R.layout.filters_fragment_list_item, parent)
        view.findViewById<ImageButton>(R.id.removeFilterIcon).setOnClickListener {
            onRemoveListener(it.parent as ViewGroup)
        }
        return MyViewHolder(view)
    }

    override fun getItemCount() = data.size

    operator fun get(index: Int) = data[index]

    fun remove(index: Int) {
        data.removeAt(index)
        notifyItemRemoved(index)
    }

    fun setData(data: List<FilterListItem>) {
        val size = this.data.size
        this.data.clear()
        notifyItemRangeRemoved(0, size)
        this.data.addAll(data)
        notifyItemRangeInserted(0, data.size)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = data[position]
        if (item.keyword.isEmpty()) {
            holder.keyword.visibility = View.GONE
        } else {
            holder.keyword.text = item.keyword
            holder.keyword.visibility = View.VISIBLE
        }
        if (item.logLevelsStr.isEmpty()) {
            holder.logLevels.visibility = View.GONE
        } else {
            holder.logLevels.text = item.logLevelsStr
            holder.logLevels.visibility = View.VISIBLE
        }
    }

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val keyword: TextView = itemView.findViewById(R.id.filterKeyword)
        val logLevels: TextView = itemView.findViewById(R.id.logLevels)
    }
}