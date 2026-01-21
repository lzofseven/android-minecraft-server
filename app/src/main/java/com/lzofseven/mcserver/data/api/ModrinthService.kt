package com.lzofseven.mcserver.data.api

import com.lzofseven.mcserver.data.model.ModrinthSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ModrinthService {
    @GET("v2/search")
    suspend fun searchProjects(
        @Query("query") query: String,
        @Query("facets") facets: String? = null, // e.g. [["categories:paper"],["project_type:mod"]]
        @Query("index") index: String = "relevance",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): ModrinthSearchResponse
    
    @retrofit2.http.GET("v2/project/{id}/version")
    suspend fun getProjectVersions(
        @retrofit2.http.Path("id") id: String,
        @Query("loaders") loaders: String? = null,
        @Query("game_versions") gameVersions: String? = null
    ): List<com.lzofseven.mcserver.data.model.ModrinthVersion>
    
    companion object {
        const val BASE_URL = "https://api.modrinth.com/"
    }
}
