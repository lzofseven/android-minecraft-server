package com.lzofseven.mcserver.data.repository

import com.lzofseven.mcserver.data.api.ModrinthService
import com.lzofseven.mcserver.data.model.ModrinthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModrinthRepository @Inject constructor(
    private val api: ModrinthService
) {
    suspend fun search(
        query: String, 
        type: String? = null, 
        version: String? = null,
        categories: List<String>? = null
    ): List<ModrinthResult> = withContext(Dispatchers.IO) {
        val facetsList = mutableListOf<List<String>>()
        
        type?.let { facetsList.add(listOf("project_type:$it")) }
        version?.let { facetsList.add(listOf("versions:$it")) }
        
        categories?.let { cats ->
            facetsList.add(cats.map { "categories:$it" })
        }
        
        val facetsJson = if (facetsList.isNotEmpty()) {
            val inner = facetsList.joinToString(",") { facet -> 
                facet.joinToString("\",\"", "[\"", "\"]") 
            }
            "[$inner]"
        } else null

        val response = api.searchProjects(query = query, facets = facetsJson)
        response.hits
    }

    suspend fun getVersions(projectId: String, loader: String? = null, gameVersion: String? = null): List<com.lzofseven.mcserver.data.model.ModrinthVersion> = withContext(Dispatchers.IO) {
        api.getProjectVersions(projectId, loader, gameVersion)
    }

    suspend fun getProject(projectId: String): com.lzofseven.mcserver.data.model.ModrinthProject = withContext(Dispatchers.IO) {
        api.getProject(projectId)
    }
}
