/*
 * This file is part of eduVPN.
 *
 * eduVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * eduVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with eduVPN.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package nl.eduvpn.app.viewmodel

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import nl.eduvpn.app.R
import nl.eduvpn.app.adapter.OrganizationAdapter
import nl.eduvpn.app.entity.AuthorizationType
import nl.eduvpn.app.entity.Instance
import nl.eduvpn.app.entity.Organization
import nl.eduvpn.app.service.*
import nl.eduvpn.app.utils.Log
import javax.inject.Inject

class OrganizationSelectionViewModel @Inject constructor(
        organizationService: OrganizationService,
        private val preferencesService: PreferencesService,
        context: Context,
        apiService: APIService,
        serializerService: SerializerService,
        historyService: HistoryService,
        connectionService: ConnectionService,
        vpnService: VPNService) : BaseConnectionViewModel(context, apiService, serializerService, historyService, preferencesService, connectionService, vpnService) {

    val state = MutableLiveData<ConnectionState>().also { it.value = ConnectionState.Ready }

    private val organizations = MutableLiveData<List<Organization>>()
    private val servers = MutableLiveData<List<Instance>>()

    val artworkVisible = MutableLiveData(true)

    val searchText = MutableLiveData("")

    init {
        state.value = ConnectionState.FetchingOrganizations
        disposables.add(
                Single.zip(organizationService.fetchOrganizations(), organizationService.fetchServerList(), BiFunction { orgList: List<Organization>, serverList: List<Instance> ->
                    Pair(orgList, serverList)
                }).subscribe({ organizationServerListPair ->
                    val organizationList = organizationServerListPair.first
                    val serverList = organizationServerListPair.second
                    organizations.value = organizationList
                    servers.value = serverList
                    state.value = ConnectionState.Ready
                }, { throwable ->
                    Log.w(TAG, "Unable to fetch organization list!", throwable)
                    parentAction.value = ParentAction.DisplayError(R.string.error_fetching_organizations, throwable.toString())
                    state.value = ConnectionState.Ready
                })
        )
    }

    val adapterItems = Transformations.switchMap(organizations) { organizations ->
        Transformations.switchMap(servers) { servers ->
            Transformations.map(searchText) { searchText ->
                val resultList = mutableListOf<OrganizationAdapter.OrganizationAdapterItem>()
                // Search contains at least two dots
                if (searchText.count { ".".contains(it) } > 1) {
                    resultList += OrganizationAdapter.OrganizationAdapterItem.Header(R.drawable.ic_server, R.string.header_connect_your_own_server)
                    resultList += OrganizationAdapter.OrganizationAdapterItem.AddServer(searchText)
                    return@map resultList
                }
                val instituteAccessServers = servers.filter {
                    it.authorizationType == AuthorizationType.Local && (searchText.isNullOrBlank() || it.displayName?.contains(searchText, ignoreCase = true) == true)
                }.sortedBy { it.displayName }
                        .map { OrganizationAdapter.OrganizationAdapterItem.InstituteAccess(it) }
                val secureInternetServers = organizations.filter {
                    if (searchText.isNullOrBlank()) {
                        true
                    } else {
                        it.displayName.contains(searchText, ignoreCase = true) || it.keywordList.any { keyword -> keyword.contains(searchText, ignoreCase = true) }
                    }
                }.mapNotNull { organization ->
                    val matchingServer = servers
                            .firstOrNull {
                                it.authorizationType == AuthorizationType.Distributed &&
                                        it.baseURI == organization.secureInternetHome
                            }
                    if (matchingServer != null) {
                        OrganizationAdapter.OrganizationAdapterItem.SecureInternet(matchingServer, organization)
                    } else {
                        null
                    }
                }
                if (instituteAccessServers.isNotEmpty()) {
                    resultList += OrganizationAdapter.OrganizationAdapterItem.Header(R.drawable.ic_institute, R.string.header_institute_access)
                    resultList += instituteAccessServers
                }
                if (secureInternetServers.isNotEmpty()) {
                    resultList += OrganizationAdapter.OrganizationAdapterItem.Header(R.drawable.ic_secure_internet, R.string.header_secure_internet)
                    resultList += secureInternetServers
                }
                resultList
            }
        }
    }

    val noItemsFound = Transformations.switchMap(state) { state ->
        Transformations.map(adapterItems) { items ->
            items.isEmpty() && state == ConnectionState.Ready
        }
    }


    fun selectOrganizationAndInstance(organization: Organization?, instance: Instance) {
        if (organization != null) {
            preferencesService.currentOrganization = organization
        }
        discoverApi(instance)
    }

    companion object {
        private val TAG = OrganizationSelectionViewModel::class.java.name
    }
}