/*
 * Copyright 2013 MicaByte Systems
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.micabytes.map

import android.content.Context
import android.graphics.*
import com.google.android.gms.common.util.Hex
import com.micabytes.gfx.SurfaceRenderer
import com.micabytes.util.Array2D
import timber.log.Timber
import kotlin.math.max

/**
 * HexMap superclass
 *
 * This implementation works for pointy-side up HexMaps. Needs to be adjusted
 * if it is going to be used for flat-side up maps.
 */
abstract class HexMap protected constructor(protected val zones: Array2D<HexTile?>) {
  var scaleFactor: Float = 0.toFloat()
  val viewPortOrigin = Point()
  val viewPortSize = Point()
  val destRect = Rect()
  var windowLeft: Int = 0
  var windowTop: Int = 0
  var windowRight: Int = 0
  var windowBottom: Int = 0
  val tilePaint = Paint()
  protected val canvas = Canvas()
  var standardOrientation = true

  val renderHeight: Int
    get() = mapHeight * tileRect.height()

  val renderWidth: Int
    get() = mapWidth * tileRect.width()

  val tileHeight: Int
    get() = tileRect.height()

  val tileWidth: Int
    get() = tileRect.width()

  init {
    tilePaint.isAntiAlias = true
    tilePaint.isFilterBitmap = true
    tilePaint.isDither = true
    val select = Paint()
    select.style = Paint.Style.STROKE
    select.color = Color.RED
    select.strokeWidth = 2f

  }

  open fun drawBase(con: Context, p: SurfaceRenderer.ViewPort) {
    if (p.bitmap == null) {
      Timber.e("Viewport bitmap is null in HexMap")
      return
    }
    canvas.setBitmap(p.bitmap)
    scaleFactor = p.zoom
    val yOffset = tileRect.height() - tileSlope
    p.getOrigin(viewPortOrigin)
    p.getSize(viewPortSize)
    windowLeft = viewPortOrigin.x
    windowTop = viewPortOrigin.y
    windowRight = viewPortOrigin.x + viewPortSize.x
    windowBottom = viewPortOrigin.y + viewPortSize.y
    var xOffset: Int
    if (standardOrientation) {
      // Clip tiles not in view
      var iMn = windowLeft / tileRect.width() - 1
      if (iMn < 0) iMn = 0
      var jMn = windowTop / (tileRect.height() - tileSlope) - 1
      if (jMn < 0) jMn = 0
      var iMx = windowRight / tileRect.width() + 2
      if (iMx >= mapWidth) iMx = mapWidth
      var jMx = windowBottom / (tileRect.height() - tileSlope) + 2
      if (jMx >= mapHeight) jMx = mapHeight
      // Draw Tiles
      for (i in iMn until iMx) {
        for (j in jMn until jMx) {
          if (zones[i, j] != null) {
            xOffset = if (j % 2 == 0) tileRect.width() / 2 else 0
            destRect.left = ((i * tileRect.width() - windowLeft - xOffset) / scaleFactor).toInt()
            destRect.top = ((j * (tileRect.height() - tileSlope) - windowTop - yOffset) / scaleFactor).toInt()
            destRect.right = ((i * tileRect.width() + tileRect.width() - windowLeft - xOffset) / scaleFactor).toInt()
            destRect.bottom = ((j * (tileRect.height() - tileSlope) + tileRect.height() - windowTop - yOffset) / scaleFactor).toInt()
            zones[i, j]?.drawBase(canvas, tileRect, destRect, tilePaint)
          }
        }
      }
    } else {
      // Clip tiles not in view
      var iMn = mapWidth - windowRight / tileRect.width() - 2
      if (iMn < 0) iMn = 0
      var jMn = mapHeight - (windowBottom / (tileRect.height() - tileSlope) + 2)
      if (jMn < 0) jMn = 0
      var iMx = mapWidth - (windowLeft / tileRect.width() + 1)
      if (iMx >= mapWidth) iMx = mapWidth - 1
      var jMx = mapHeight - (windowTop / (tileRect.height() - tileSlope) + 1)
      if (jMx >= mapHeight) jMx = mapHeight - 1
      // Draw Tiles
      for (i in iMx downTo iMn) {
        for (j in jMx downTo jMn) {
          if (zones[i, j] != null) {
            xOffset = if (j % 2 == 1) tileRect.width() / 2 else 0
            destRect.left = (((mapWidth - i - 1) * tileRect.width() - windowLeft - xOffset) / scaleFactor).toInt()
            destRect.top = (((mapHeight - j - 1) * (tileRect.height() - tileSlope) - windowTop - yOffset) / scaleFactor).toInt()
            destRect.right = (((mapWidth - i - 1) * tileRect.width() + tileRect.width() - windowLeft - xOffset) / scaleFactor).toInt()
            destRect.bottom = (((mapHeight - j - 1) * (tileRect.height() - tileSlope) + tileRect.height() - windowTop - yOffset) / scaleFactor).toInt()
            zones[i, j]?.drawBase(canvas, tileRect, destRect, tilePaint)
          }
        }
      }
    }
  }

  abstract fun drawLayer(context: Context, p: SurfaceRenderer.ViewPort)

  abstract fun drawFinal(context: Context, p: SurfaceRenderer.ViewPort)

  abstract fun getViewPortOrigin(x: Int, y: Int, p: SurfaceRenderer.ViewPort): Point

  internal fun distance(from: HexTile, to: HexTile): Int =
    (Math.abs(from.cubeX - to.cubeX) + Math.abs(from.cubeY - to.cubeY) + Math.abs(from.cubeZ - to.cubeZ)) / 2

  fun get(cube: Triple<Int, Int, Int>): HexTile? {
    val pos = cubeToOddR(cube)
    return zones[pos.first, pos.second]
  }

  fun hex_linedraw(a: HexTile, b: HexTile): List<HexTile> {
    val N = distance(a, b)
    val results = ArrayList<HexTile>()
    val step: Double = 1.0 / max(N, 1);
    for (i in 0..N) {
      val cube = cube_round(
        hex_lerp(
          Triple(a.cubeX.toDouble(), a.cubeY.toDouble(), a.cubeZ.toDouble()),
          Triple(b.cubeX.toDouble(), b.cubeY.toDouble(), b.cubeZ.toDouble()),
          step * i
        )
      )
      val tile = get(cube)
      if (tile != null) results.add(tile)
    }
    return results;
  }

  fun isLineOfSight(a: HexTile, b: HexTile): Boolean {
    val N = distance(a, b)
    val step: Double = 1.0 / max(N, 1);
    for (i in 1 until N) {
      val cube1 = cube_round(hex_lerp(Triple(a.cubeX + 1e-6, a.cubeY + 1e-6, a.cubeZ - 2e-6), Triple(b.cubeX + 1e-6, b.cubeY + 1e-6, b.cubeZ - 2e-6), step * i))
      val cube2 = cube_round(hex_lerp(Triple(a.cubeX - 1e-6, a.cubeY - 1e-6, a.cubeZ + 2e-6), Triple(b.cubeX - 1e-6, b.cubeY - 1e-6, b.cubeZ + 2e-6), step * i))
      val tile1 = get(cube1)
      val tile2 = get(cube2)
      if ((tile1 != a && tile1 != b && tile1?.isOpaque() == true) &&
        (tile2 != a && tile2 != b && tile2?.isOpaque() == true)
      )
        return false
    }
    return true
  }

  companion object {
    var mapWidth = 0
    var mapHeight = 0
    var tileSlope = 0
    var tileRect = Rect()
  }

}
