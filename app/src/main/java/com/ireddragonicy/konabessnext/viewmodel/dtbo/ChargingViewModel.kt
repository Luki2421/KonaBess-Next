package com.ireddragonicy.konabessnext.viewmodel.dtbo

import androidx.lifecycle.ViewModel
import com.ireddragonicy.konabessnext.domain.ChargingDomainManager
import com.ireddragonicy.konabessnext.repository.DtboRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChargingViewModel @Inject constructor(
    private val repository: DtboRepository,
    private val chargingManager: ChargingDomainManager
) : ViewModel() {
    val chargingNodes = repository.chargingNodes

    fun parseCells(input: String): List<Long>? = chargingManager.parseUserCells(input)

    fun updateProperty(fragmentIndex: Int, path: List<String>, propertyName: String, cells: List<Long>) {
        repository.updateChargingProperty(fragmentIndex, path, propertyName, cells)
    }
}
