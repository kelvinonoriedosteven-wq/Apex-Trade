package com.example.data.repository

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(val reply: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class AiAssistantRepository {

    private val customerModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    """
                    You are ApexTrade Client Copilot, a read-only brokerage education and portfolio analytics assistant.
                    Strict Rules:
                    - You CANNOT execute trades, modify cash balances, approve KYC, approve deposits, or settle withdrawals. If asked to perform an action, explain the exact in-app screen where the user submits it.
                    - Remind users when relevant that all KYC, deposit proofs, and withdrawals enter Pending status for 24-hour review.
                    - Provide factual portfolio breakdown, diversification commentary, order type explanations (Market, Limit, Stop, Stop-Limit), and risk awareness. Never promise or guarantee financial returns.
                    - Keep answers concise, structured with bullet points, and grounded only in the context supplied.
                    """.trimIndent()
                )
            }
        )
    }

    private val adminModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    """
                    You are ApexTrade Risk & Operations Copilot for verified Brokerage Administrators.
                    Strict Rules:
                    - You are strictly READ-ONLY. Never claim to have approved KYC, credited funds, processed payouts, or edited commission rates.
                    - Help admins summarize operational queues, interpret AML risk alerts, check ledger reconciliation health, and draft compliant customer notices or email templates.
                    - Highlight dual-control Maker-Checker requirements for payouts or adjustments above the configured threshold.
                    - Keep responses concise, factual, and executive-ready.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun askCustomerCopilot(
        prompt: String,
        portfolioContext: String
    ): Result<String> {
        if (prompt.isBlank()) return Result.failure(IllegalArgumentException("Enter a question."))
        return try {
            val responseText = withTimeout(TIMEOUT_MS) {
                val fullPrompt = """
                    LIVE USER ACCOUNT & PORTFOLIO CONTEXT (READ-ONLY):
                    $portfolioContext

                    USER QUESTION:
                    ${prompt.trim()}
                """.trimIndent()
                val response = customerModel.generateContent(fullPrompt)
                response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Empty response from Gemini AI.")
            }
            Result.success(responseText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(formatAiError(e), e))
        }
    }

    suspend fun askAdminCopilot(
        prompt: String,
        operationsContext: String
    ): Result<String> {
        if (prompt.isBlank()) return Result.failure(IllegalArgumentException("Enter an operational prompt."))
        return try {
            val responseText = withTimeout(TIMEOUT_MS) {
                val fullPrompt = """
                    LIVE BROKERAGE OPERATIONS SNAPSHOT (READ-ONLY):
                    $operationsContext

                    ADMIN REQUEST:
                    ${prompt.trim()}
                """.trimIndent()
                val response = adminModel.generateContent(fullPrompt)
                response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Empty response from Gemini AI.")
            }
            Result.success(responseText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(formatAiError(e), e))
        }
    }

    private fun formatAiError(error: Exception): String {
        val msg = error.localizedMessage ?: error.message ?: "AI request failed"
        return when {
            msg.contains("403") || msg.contains("AppCheck", ignoreCase = true) ->
                "AI attestation failed (App Check 403). Ensure Firebase App Check debug token or reCAPTCHA key is active."
            msg.contains("timeout", ignoreCase = true) ->
                "AI request timed out after 60s. Please retry."
            else -> msg
        }
    }

    private companion object {
        const val MODEL_NAME = "gemini-3.8-flash"
        const val TIMEOUT_MS = 60_000L
    }
}
