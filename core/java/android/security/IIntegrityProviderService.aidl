package android.security;

/**
 * interface to communicate with IntegrityProviderService.
 * @hide
 */
interface IIntegrityProviderService {
    String requestToken(String tokenReq);
}
