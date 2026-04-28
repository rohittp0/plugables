package com.rohittp.plugables.sample

import com.rohittp.plugables.viewmodelstub.ViewModelStub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sample ViewModel that exercises the viewmodel-stub plugin. The plugin generates
 * a `HomeViewModel` interface and a `HomeViewModelStub` class from this Impl.
 */
@ViewModelStub
class HomeViewModelImpl {

    val isLoading: StateFlow<Boolean> = MutableStateFlow(false)

    val title: String = "Home"

    val errorMessage: String? = null

    fun refresh() {
        // real implementation
    }

    suspend fun loadDetails(itemId: String): String {
        return "details for $itemId"
    }
}
