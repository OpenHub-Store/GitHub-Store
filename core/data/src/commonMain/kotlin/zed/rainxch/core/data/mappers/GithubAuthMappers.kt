package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.GithubDeviceStartDto
import zed.rainxch.core.data.dto.GithubDeviceTokenErrorDto
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.domain.model.GithubDeviceStart
import zed.rainxch.core.domain.model.GithubDeviceTokenError
import zed.rainxch.core.domain.model.GithubDeviceTokenSuccess

fun GithubDeviceStartDto.toDomain() =
    GithubDeviceStart(
        deviceCode = deviceCode,
        userCode = userCode,
        verificationUri = verificationUri,
        verificationUriComplete = verificationUriComplete,
        intervalSec = intervalSec,
        expiresInSec = expiresInSec,
    )

fun GithubDeviceTokenSuccessDto.toDomain() =
    GithubDeviceTokenSuccess(
        accessToken = accessToken,
        tokenType = tokenType,
        expiresIn = expiresIn,
        scope = scope,
        refreshToken = refreshToken,
        refreshTokenExpiresIn = refreshTokenExpiresIn,
    )

fun GithubDeviceTokenErrorDto.toDomain() =
    GithubDeviceTokenError(
        error = error,
        errorDescription = errorDescription,
    )

fun GithubDeviceStart.toData() =
    GithubDeviceStartDto(
        deviceCode = deviceCode,
        userCode = userCode,
        verificationUri = verificationUri,
        verificationUriComplete = verificationUriComplete,
        intervalSec = intervalSec,
        expiresInSec = expiresInSec,
    )

fun GithubDeviceTokenSuccess.toData() =
    GithubDeviceTokenSuccessDto(
        accessToken = accessToken,
        tokenType = tokenType,
        expiresIn = expiresIn,
        scope = scope,
        refreshToken = refreshToken,
        refreshTokenExpiresIn = refreshTokenExpiresIn,
    )

fun GithubDeviceTokenError.toData() =
    GithubDeviceTokenErrorDto(
        error = error,
        errorDescription = errorDescription,
    )
