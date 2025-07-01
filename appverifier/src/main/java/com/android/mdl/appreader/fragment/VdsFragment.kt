package com.android.mdl.appreader.fragment

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import com.android.mdl.appreader.R

class VdsFragment : DialogFragment() {
    
    private var onContinueListener: (() -> Unit)? = null
    private var verificationData: Map<String, String>? = null
    
    fun setOnContinueListener(listener: () -> Unit) {
        onContinueListener = listener
    }

    fun setVerificationData(data: Map<String, String>) {
        verificationData = data
    }

    private fun createSectionTitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dpToPx(context, 8f).toInt())
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(context, 8f).toInt(), 0, dpToPx(context, 8f).toInt())
            }
        }
    }
    
    private fun createDivider(context: Context): View {
        return View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, com.google.android.material.R.color.material_on_surface_stroke))
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 1f).toInt()
            ).apply {
                setMargins(0, dpToPx(context, 8f).toInt(), 0, dpToPx(context, 8f).toInt())
            }
        }
    }
    
    private fun createInfoItem(context: Context, label: String, value: String, isBold: Boolean = false): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    dpToPx(context, 16f).toInt(),
                    dpToPx(context, 4f).toInt(),
                    dpToPx(context, 16f).toInt(),
                    dpToPx(context, 4f).toInt()
                )
            }
            
            addView(TextView(context).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(ContextCompat.getColor(context, com.google.android.material.R.color.material_on_surface_emphasis_medium))
            })
            
            addView(TextView(context).apply {
                text = value.ifEmpty { getString(R.string.not_provided) }
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(0, dpToPx(context, 2f).toInt(), 0, 0)
                if (isBold) {
                    setTypeface(null, Typeface.BOLD)
                }
            })
        }
    }
    
    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(context, 16f).toInt())
        }
        
        scrollView.addView(container)
        
        verificationData?.let { data ->
            // Document Holder Section
            container.addView(createSectionTitle(context, getString(R.string.document_holder)))
            
            // Name
            val fullName = "${data["firstName"] ?: ""} ${data["lastName"] ?: ""} ${data["commonName"] ?: ""}"
                .trim()
                .replace("\\s+".toRegex(), " ")
            container.addView(createInfoItem(context, "Full Name", fullName, isBold = true))
            
            // Date of Birth
            data["dateOfBirth"]?.let { dob ->
                container.addView(createInfoItem(context, "Date of Birth", dob, isBold = true))
            }
            
            // Document Details Section
            container.addView(createDivider(context))
            container.addView(createSectionTitle(context, getString(R.string.document_details)))
            
            // Issuance Date
            data["issuanceDate"]?.let { issuanceDate ->
                container.addView(createInfoItem(context, "Issuance Date", issuanceDate))
            }

            // Issuance Elapsed Time
            data["issuanceElapsedTime"]?.let { issuanceElapsedTime ->
                container.addView(createInfoItem(context, "Issued Since", issuanceElapsedTime))
            }

            // Signer Name
            data["certificateReference"]?.let { certificateReference ->
                container.addView(createInfoItem(context, "Signer", certificateReference))
            }

            // Deciphered Block
            data["decipheredBlock"]?.let { decipheredBlock ->
                container.addView(createInfoItem(context, "Decrypted Block", decipheredBlock))
            }
            
            // Error message if present
            data["errorMessage"]?.let { error ->
                if (error.isNotEmpty()) {
                    container.addView(createDivider(context))
                    val errorView = TextView(context).apply {
                        text = error
                        setTextColor(ContextCompat.getColor(context, com.google.android.material.R.color.design_default_color_error))
                        setPadding(dpToPx(context, 16f).toInt())
                        setBackgroundColor(ContextCompat.getColor(context, com.google.android.material.R.color.material_slider_inactive_tick_marks_color))
                        layoutParams = ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, dpToPx(context, 8f).toInt(), 0, 0)
                        }
                    }
                    container.addView(errorView)
                }
            }
        }
        
        val dialog = AlertDialog.Builder(context)
            .setView(scrollView)
            .setTitle(R.string.document_information)
            .setPositiveButton(R.string.continue_text) { _, _ ->
                onContinueListener?.invoke()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Show the dialog first to ensure the title view is available
        dialog.show()

        // Check conditions for showing the green tick
        val showGreenTick = verificationData?.get("decipheredBlock") == "true" && 
                          (verificationData?.get("certificateReference")?.contains("staticBlock") == true) &&
                          (verificationData?.get("certificateReference")?.contains("dynamicBlock") == true)

        if (showGreenTick) {
            try {
                // Get the title text view
                val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
                val titleView = dialog.window?.decorView?.findViewById<TextView>(titleId)
                
                // Add a green checkmark icon
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_green_24dp)
                titleView?.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    drawable,
                    null
                )
                titleView?.compoundDrawablePadding = 16
            } catch (e: Exception) {
                Log.e("VdsFragment", "Error adding green tick", e)
            }
        }

        return dialog
    }
    
    companion object {
        fun newInstance(
            firstName: String = "",
            lastName: String = "",
            commonName: String = "",
            dateOfBirth: String = "",
            issuanceDate: String = "",
            issuanceElapsedTime: String = "",
            certificateReference: String = "",
            decipheredBlock: String = "",
            errorMessage: String = "",
            onContinue: () -> Unit
        ): VdsFragment {
            return VdsFragment().apply {
                setVerificationData(
                    mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "commonName" to commonName,
                        "dateOfBirth" to dateOfBirth,
                        "issuanceDate" to issuanceDate,
                        "issuanceElapsedTime" to issuanceElapsedTime,
                        "certificateReference" to certificateReference,
                        "decipheredBlock" to decipheredBlock,
                        "errorMessage" to errorMessage
                    )
                )
                setOnContinueListener(onContinue)
            }
        }
    }
}