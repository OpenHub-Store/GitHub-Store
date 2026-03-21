package zed.rainxch.details.data.system

import zed.rainxch.core.domain.logging.GitHubStoreLogger
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.system.AttestationVerifier
import zed.rainxch.details.domain.system.VerificationResult
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class AttestationVerifierImpl(
    private val detailsRepository: DetailsRepository,
    private val logger: GitHubStoreLogger,
) : AttestationVerifier {
    override suspend fun verify(
        owner: String,
        repoName: String,
        filePath: String,
    ): VerificationResult =
        try {
            val digest = computeSha256(filePath)
            val hasAttestation = detailsRepository.checkAttestations(owner, repoName, digest)
            if (hasAttestation) VerificationResult.Verified else VerificationResult.Unverified
        } catch (e: Exception) {
            logger.debug("Attestation check error: ${e.message}")
            VerificationResult.Error(e.message ?: "Unknown error")
        }

    private fun computeSha256(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(File(filePath)).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
