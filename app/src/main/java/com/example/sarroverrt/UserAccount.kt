package com.example.sarroverrt

data class UserAccount(
    val email: String,
    val username: String,
    val password: String, // Only for testing as you requested
    val profilePicUri: String? = null
)