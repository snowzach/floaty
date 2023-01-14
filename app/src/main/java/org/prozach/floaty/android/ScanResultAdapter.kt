package org.prozach.floaty.android

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.prozach.floaty.android.databinding.RowScanResultBinding

class ScanResultAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =  RowScanResultBinding.inflate(LayoutInflater.from(parent.context), parent,false)
        return ViewHolder(view, onClickListener)
    }

    override fun getItemCount() = items.size

    class ViewHolder(
        val binding: RowScanResultBinding,
        val onClickListener: ((device: ScanResult) -> Unit)
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder){
            with(items[position]){
                binding.deviceName.text = this.device.name ?: "Unnamed"
                binding.macAddress.text = this.device.address
                binding.signalStrength.text = "${this.rssi} dBm"
                binding.root.setOnClickListener{ onClickListener.invoke(this) }
            }
        }
    }
}
