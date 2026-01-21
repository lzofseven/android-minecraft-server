package com.lzofseven.mcserver.data.model

import com.google.gson.annotations.SerializedName

data class ModrinthSearchResponse(
    val hits: List<ModrinthResult>,
    val limit: Int,
    val offset: Int,
    val total_hits: Int
)

data class ModrinthResult(
    @SerializedName("project_id") val projectId: String,
    val title: String,
    val description: String,
    val author: String,
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("project_type") val projectType: String,
    val downloads: Int,
    val follows: Int,
    @SerializedName("display_categories") val categories: List<String>?,
    val versions: List<String>?
)

data class ModrinthVersion(
    val id: String,
    @SerializedName("project_id") val projectId: String,
    @SerializedName("version_number") val versionNumber: String,
    val files: List<ModrinthFile>,
    @SerializedName("game_versions") val gameVersions: List<String>,
    val loaders: List<String>,
    @SerializedName("version_type") val versionType: String, // release, beta, alpha
    @SerializedName("date_published") val datePublished: String
)

data class ModrinthFile(
    val url: String,
    val filename: String,
    val primary: Boolean,
    val size: Long
)

data class ModrinthProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val body: String, // Markdown
    @SerializedName("body_url") val bodyUrl: String?,
    @SerializedName("published") val published: String,
    @SerializedName("updated") val updated: String,
    val status: String,
    @SerializedName("allow_mod_distribution") val allowModDistribution: Boolean?,
    @SerializedName("game_versions") val gameVersions: List<String>,
    val loaders: List<String>,
    @SerializedName("icon_url") val iconUrl: String?,
    @SerializedName("wiki_url") val wikiUrl: String?,
    @SerializedName("issues_url") val issuesUrl: String?,
    @SerializedName("source_url") val sourceUrl: String?,
    val downloads: Int,
    val followers: Int
)
