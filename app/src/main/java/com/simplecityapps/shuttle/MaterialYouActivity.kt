package com.simplecityapps.shuttle

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi

@RequiresApi(31)
class MaterialYouActivity : ComponentActivity() {

    private fun Resources.getColorIdentifier(
        name: String,
        defPackage: String = "android"
    ) = getIdentifier(name, "color", defPackage)

    private val accentMaker: (name: String) -> List<Int> = { name ->
        listOf(
            getColor(resources.getColorIdentifier(name = "system_${name}_0")),
            getColor(resources.getColorIdentifier(name = "system_${name}_10")),
            getColor(resources.getColorIdentifier(name = "system_${name}_100")),
            getColor(resources.getColorIdentifier(name = "system_${name}_200")),
            getColor(resources.getColorIdentifier(name = "system_${name}_300")),
            getColor(resources.getColorIdentifier(name = "system_${name}_400")),
            getColor(resources.getColorIdentifier(name = "system_${name}_500")),
            getColor(resources.getColorIdentifier(name = "system_${name}_600")),
            getColor(resources.getColorIdentifier(name = "system_${name}_700")),
            getColor(resources.getColorIdentifier(name = "system_${name}_800")),
            getColor(resources.getColorIdentifier(name = "system_${name}_900")),
            getColor(resources.getColorIdentifier(name = "system_${name}_1000"))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.material_you_activity)
        val itemMaker: ViewGroup.(Int) -> View = { accent ->
            LayoutInflater.from(context).inflate(R.layout.material_you_item, this, false).apply {
                setBackgroundColor(accent)
            }
        }
        findViewById<LinearLayout>(R.id.colorContainer).apply {
            accentMaker("accent1").forEach { accent ->
                addView(itemMaker(accent))
            }
            accentMaker("accent2").forEach { accent ->
                addView(itemMaker(accent))
            }
            accentMaker("accent3").forEach { accent ->
                addView(itemMaker(accent))
            }
            accentMaker("neutral1").forEach { accent ->
                addView(itemMaker(accent))
            }
            accentMaker("neutral2").forEach { accent ->
                addView(itemMaker(accent))
            }
        }
    }

}
