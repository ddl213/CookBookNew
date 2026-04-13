package com.android.common.base.adapter.refresh.header

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.common.base.adapter.refresh.header.listener.IRefreshHeader

class RefreshHeaderAdapter(private val header: IRefreshHeader) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return object : RecyclerView.ViewHolder(header.getHeaderView()) {}
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
}