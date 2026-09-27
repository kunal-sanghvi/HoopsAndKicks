package com.hoopsandkicks.tournament.data

const val WEB_VIEWER_URL = "https://kunal-sanghvi.github.io/HoopsAndKicks/"

fun roomShareUrl(code: String): String = "$WEB_VIEWER_URL?room=$code"

fun roomShareText(tournamentName: String, code: String): String =
    "Watch $tournamentName live: ${roomShareUrl(code)}\n" +
        "Or in the Hoops & Kicks app: tap “Have a room code?” and enter $code"
