package com.jongwook.siteboard

import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException

object GoogleSignInErrorFormatter {

    fun format(error: Exception): String {
        val apiException = error as? ApiException ?: return "로그인 실패: ${error.message ?: "알 수 없는 오류"}"
        val detail = when (apiException.statusCode) {
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "로그인이 취소되었습니다."
            GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "로그인이 이미 진행 중입니다."
            10 -> "개발자 설정 오류입니다. 패키지명, SHA-1, OAuth 클라이언트 설정을 확인하세요."
            else -> apiException.statusCode.toString()
        }
        return "로그인 실패(${apiException.statusCode}): $detail"
    }
}
