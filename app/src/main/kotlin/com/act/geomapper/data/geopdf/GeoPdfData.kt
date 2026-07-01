package com.act.geomapper.data.geopdf

import android.graphics.Bitmap

data class GeoPdfData(
    val nombre : String,
    val bitmap : Bitmap,
    val norte  : Double,   // latitud máxima
    val sur    : Double,   // latitud mínima
    val este   : Double,   // longitud máxima
    val oeste  : Double    // longitud mínima
)
