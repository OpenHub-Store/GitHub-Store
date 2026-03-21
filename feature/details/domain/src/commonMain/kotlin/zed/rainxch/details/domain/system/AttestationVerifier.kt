package zed.rainxch.details.domain.system

/**
 * Verifies build attestations for downloaded assets using GitHub's
 * supply-chain security API.
 */
interface AttestationVerifier {
    /**
     * Computes the SHA-256 digest of [filePath] and checks whether
     * the repository [owner]/[repoName] has a matching attestation.
     *
     * @return [VerificationResult.Verified] if a valid attestation exists,
     *         [VerificationResult.Unverified] if no matching attestation was found,
     *         [VerificationResult.Error] if the check could not be completed.
     */
    suspend fun verify(
        owner: String,
        repoName: String,
        filePath: String,
    ): VerificationResult
}

sealed interface VerificationResult {
    data object Verified : VerificationResult

    data object Unverified : VerificationResult

    data class Error(val reason: String) : VerificationResult
}
