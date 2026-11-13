package com.crystaln.vpn.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.google.android.material.card.MaterialCardView

class ProfilesAdapter(
    private var profiles: List<Profile>,
    private var selectedId: String?,
    private val onSelect: (Profile) -> Unit,
    private val onEdit: (Profile) -> Unit,
    private val onDelete: (Profile) -> Unit,
) : RecyclerView.Adapter<ProfilesAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.profileCard)
        val name: TextView = view.findViewById(R.id.profileName)
        val summary: TextView = view.findViewById(R.id.profileSummary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

    override fun getItemCount(): Int = profiles.size

    override fun onBindViewHolder(h: Holder, position: Int) {
        val p = profiles[position]
        h.name.text = p.name
        h.summary.text = p.summary()
        h.card.strokeWidth = if (p.id == selectedId) {
            h.card.context.resources.displayMetrics.density.let { (2 * it).toInt() }
        } else {
            0
        }
        h.card.setOnClickListener { onSelect(p) }
        h.itemView.findViewById<View>(R.id.editButton).setOnClickListener { onEdit(p) }
        h.itemView.findViewById<View>(R.id.deleteButton).setOnClickListener { onDelete(p) }
    }

    fun submit(list: List<Profile>, selected: String?) {
        profiles = list
        selectedId = selected
        notifyDataSetChanged()
    }
}
