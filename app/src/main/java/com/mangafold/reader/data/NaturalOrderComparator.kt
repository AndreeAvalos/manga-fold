package com.mangafold.reader.data

/**
 * Ordena "pagina2.jpg" antes que "pagina10.jpg" comparando los tramos numéricos
 * como números en vez de carácter a carácter, que es como se nombran casi
 * todas las páginas de manga escaneado.
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val numA = a.substring(i, ei).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(j, ej).trimStart('0').ifEmpty { "0" }
                val cmp = if (numA.length != numB.length) numA.length - numB.length else numA.compareTo(numB)
                if (cmp != 0) return cmp
                i = ei
                j = ej
            } else {
                if (ca != cb) return ca.compareTo(cb)
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
