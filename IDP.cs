using Jose;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Org.BouncyCastle.Asn1;
using Org.BouncyCastle.Asn1.TeleTrust;
using Org.BouncyCastle.Asn1.X9;
using Org.BouncyCastle.Crypto;
using Org.BouncyCastle.Crypto.Agreement;
using Org.BouncyCastle.Crypto.Agreement.Kdf;
using Org.BouncyCastle.Crypto.Digests;
using Org.BouncyCastle.Crypto.Generators;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Math;
using Org.BouncyCastle.Security;
using Org.BouncyCastle.Utilities;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Threading.Tasks;
using System.Web;

namespace TI.TS
{
    /// <summary>
    /// Implementierungsleitfaden: https://partnerportal.kv-telematik.de/display/TSSSPEC/Authentisierung+-+Implementierungsleitfaden
    /// </summary>
    internal class IDP
    {
        private const string C_URI_REDIRECT = "https://localhost/";
        private const string C_CLIENT_ID = "116117TerminserviceApp";

        private LogExt m_oLog = new LogExt();

        private Konnektor m_oKonnektor = null;

        private readonly HttpClient m_oHttpClientDefault;
        private string m_strIdpServerUrl = "https://​ti-auth-server.eterminservice.kv-safenet.de";      // Default: Produktiv
        private string m_strIdpServerUrlDD = string.Empty;
        private string m_strUriDisc = string.Empty;
        private string m_strUriPukIdpSig = string.Empty;
        private string m_strUriPukIdpEnc = string.Empty;
        private string m_strUriAuthorizationEndpoint = string.Empty;
        private string m_strUriTokenEndpoint = string.Empty;

        private string m_strUriAuthorizationCodeFlow = string.Empty;

        private string m_strHttpResponseDdJwt = string.Empty;
        private DateTime m_dtExp = DateTime.MinValue;
        private DateTime m_dtIat = DateTime.MinValue;
        private JArray m_oScopesSupported;
        private string m_strHttpChallengeResponseString = string.Empty;
        private string m_strChallengeToken = string.Empty;
        private string m_strPukIdpEnc = string.Empty;
        private string m_strPukIdpSig = string.Empty;
        private string m_strChallengeTokenDecoded = string.Empty;
        private JObject m_oChallengeTokenDecoded = null;
        private string m_strChallengeTokenCodeChallenge = string.Empty; private string m_strJwePayloadJson = string.Empty;
        private ECPublicKeyParameters m_oIdpEncKeyPublic = null;
        private long m_lExpChallengeToken = 0;
        private string m_strTokenEndpointCode = string.Empty;
        private HttpContent m_oHttpContentPostSignedCodeChallenge = null;
        private HttpContent m_oHttpContentPostTokenEndpoint = null;
        private byte[] m_byteRawAesTokenKey = new byte[32];

        private readonly Org.BouncyCastle.Security.SecureRandom m_oRandom = new();
        private string m_strCodeVerifier = string.Empty;

        private string m_strLastErrorUserDE = string.Empty;
        private string m_strLastErrorExpert = string.Empty;
        private TimeSpan m_timespanCreateNewBearerToken;


        internal IDP(int iTimeoutSeconds)
        {
            m_oHttpClientDefault = new HttpClient();
            m_oHttpClientDefault.Timeout = new TimeSpan(0, 0, iTimeoutSeconds);
        }


        #region GET-SET-IDP
        public void SetLogging(LogExt oLog)
        {
            m_oLog = oLog;
            m_oLog.Activate();
        }

        internal void SetKonnektor(Konnektor oKonnektor)
        {
            m_oKonnektor = oKonnektor;
        }


        internal void SetProduktivumgebung()
        {
            m_strIdpServerUrl = "https://​ti-auth-server.eterminservice.kv-safenet.de";
        }


        internal void SetReferenzumgebung()
        {
             m_strIdpServerUrl = "https://ref1-ets-ti-auth-server.kv-telematik.de";
        }

        //internal string GetFirewallInfoPrettyPrint()
        //{
        //    StringBuilder sbFirewallInfo = new StringBuilder();

        //    return sbFirewallInfo.ToString();
        //}

        internal string GetLastErrorUserDE()
        {
            return m_strLastErrorUserDE;
        }

        #endregion GET-SET-IDP

         
        #region TEST-IDP

        internal bool CheckConnectionREF()
        {
            m_oLog.LogFileAddLineInfo($"CheckConnection ...");

            bool bIsOk = false;

            DateTime datetimeStart = DateTime.Now;

            string strResult = string.Empty;
            int iStatusCode;
            string strStatusCode = string.Empty;

            bool bIsOkGetDiscoveryDocument = HTTPGet("https://ref1-ets-ti-auth-server.kv-telematik.de/.well-known/openid-configuration.unsigned", "GetDiscoveryDocument", out strResult, out iStatusCode, out strStatusCode);

            bool bIsOkGetPukIdpEnc = HTTPGet("https://ref1-ets-ti-auth-server.kv-telematik.de/idpEnc/jwk.json", "GetPukIdpEnc", out strResult, out iStatusCode, out strStatusCode);

            bool bIsOkGetPukIdpSig = HTTPGet("https://ref1-ets-ti-auth-server.kv-telematik.de/idpSig/jwk.json", "GetPukIdpSig", out strResult, out iStatusCode, out strStatusCode);

            bIsOk = bIsOkGetDiscoveryDocument && bIsOkGetPukIdpEnc && bIsOkGetPukIdpSig;

            m_oLog.LogFileAddLineInfo($"CheckConnection() in {(DateTime.Now-datetimeStart).TotalSeconds}s: {bIsOk}");

            return bIsOk;
        }

        #endregion TEST-IDP


        #region USECASES-IDP
        internal string CreateNewBearerToken(string strScope)
        {
            string strBearerToken = string.Empty;

            DateTime dtStart = DateTime.Now;

            if(HTTPGetDiscoveryDocumentUnsigned())
            {
                if(ResolveDiscoveryDocument())
                {
                    if(AuthorisationCodeFlowPrepare(strScope))
                    {
                        if(HttpGetInitAuthorisationCodeFlow())
                        {
                            if(ResolveAuthorisationCodeFlow())
                            {
                                if(GetPukIdpEnc())
                                {
                                    if(GetPukIdpSig())
                                    {
                                        if(DecodePukIdpSig())
                                        {
                                            X509Certificate2 oSmcbCert = m_oKonnektor.ReadSmcbCertiticate();
                                            if (SignCodeChallenge(oSmcbCert, true))                                                 // TODO RSA oder ECC?
                                            {
                                                HttpClient oHttpClient1 = null;
                                                if(PreparePostSignedCodeChallenge(out oHttpClient1))
                                                {
                                                    if (PostSignedCodeChallenge(oHttpClient1))
                                                    {
                                                        HttpClient oHttpClient2 = null;
                                                        if (PreparePostTokenEndpoint(out oHttpClient2))
                                                        {
                                                            if(PostTokenEndpoint(oHttpClient2, out strBearerToken))
                                                            {
                                                                m_timespanCreateNewBearerToken = DateTime.Now - dtStart;
                                                                m_oLog.LogFileAddLineInfo($"CreateNewBearerToken took {m_timespanCreateNewBearerToken.TotalSeconds}s");
                                                            }
                                                            else
                                                            {
                                                                m_strLastErrorUserDE = $"Kritischer Fehler in PreparePostTokenEndpoint: {m_strLastErrorExpert}";
                                                                strBearerToken = string.Empty;
                                                            }
                                                        }
                                                        else
                                                        {
                                                            m_strLastErrorUserDE = $"Kritischer Fehler in PreparePostTokenEndpoint: {m_strLastErrorExpert}";
                                                            strBearerToken = string.Empty;
                                                        }
                                                    }
                                                    else
                                                    {
                                                        m_strLastErrorUserDE = $"Kritischer Fehler in PostSignedCodeChallenge: {m_strLastErrorExpert}";
                                                        strBearerToken = string.Empty;
                                                    }
                                                }
                                                else
                                                {
                                                    m_strLastErrorUserDE = $"Kritischer Fehler in PreparePostSignedCodeChallenge: {m_strLastErrorExpert}";
                                                    strBearerToken = string.Empty;
                                                }
                                            }
                                            else
                                            {
                                                m_strLastErrorUserDE = $"Kritischer Fehler in SignCodeChallenge: {m_strLastErrorExpert}";
                                                strBearerToken = string.Empty;
                                            }
                                        }
                                        else
                                        {
                                            m_strLastErrorUserDE = $"Kritischer Fehler in DecodePukIdpSig: {m_strLastErrorExpert}";
                                            strBearerToken = string.Empty;
                                        }
                                    }
                                    else
                                    {
                                        m_strLastErrorUserDE = $"Fehler beim Verbindungsaufbau! Bitte Systemadministrator informieren: {m_strLastErrorExpert}";
                                        strBearerToken = string.Empty;
                                    }
                                }
                                else
                                {
                                    m_strLastErrorUserDE = $"Fehler beim Verbindungsaufbau! Bitte Systemadministrator informieren: {m_strLastErrorExpert}";
                                    strBearerToken = string.Empty;
                                }
                            }
                            else
                            {
                                m_strLastErrorUserDE = $"Kritischer Fehler in ResolveAuthorisationCodeFlow: {m_strLastErrorExpert}";
                                strBearerToken = string.Empty;
                            }
                        }
                        else
                        {
                            m_strLastErrorUserDE = $"Fehler beim Verbindungsaufbau! Bitte Systemadministrator informieren: {m_strLastErrorExpert}";
                            strBearerToken = string.Empty;
                        }
                    }
                    else
                    {
                        m_strLastErrorUserDE = $"Kritischer Fehler in AuthorisationCodeFlowPrepare(): {m_strLastErrorExpert}";
                        strBearerToken = string.Empty;
                    }
                }
                else
                {
                    m_strLastErrorUserDE = $"Kritischer Fehler in ResolveDiscoveryDocument(): {m_strLastErrorExpert}";
                    strBearerToken = string.Empty;
                }
            }
            else
            {
                m_strLastErrorUserDE = $"Fehler beim Verbindungsaufbau! Bitte Systemadministrator informieren: {m_strLastErrorExpert}";
                strBearerToken = string.Empty;
            }

            return strBearerToken;
        }

        #endregion USECASES-IDP


        #region USECASES-HTTP-REQUESTS-IDP

        /// <summary>
        /// First connect to IDP:
        /// HTTP GET  https://ref1-ets-ti-auth-server.kv-telematik.de/.well-known/openid-configuration.unsigned     (url is REF1 - not production):
        /// 
        /// RESULT    discovery document (json format)
        /// </summary>
        private bool HTTPGetDiscoveryDocumentUnsigned()
        {
            bool bIsConnected = false;

            m_oLog.LogFileAddLineInfo($"IDP.HTTPGetDiscoveryDocument() ...");

            m_strHttpResponseDdJwt = string.Empty;

            m_strIdpServerUrlDD = $"{m_strIdpServerUrl}/.well-known/openid-configuration.unsigned";


            string strResult = string.Empty;
            int iStatusCode;
            string strStatusCode = string.Empty;
            bIsConnected = HTTPGet(m_strIdpServerUrlDD, "GetDiscoveryDocumentUnsigned", out strResult, out iStatusCode, out strStatusCode);
            if(bIsConnected)
            {
                m_strHttpResponseDdJwt = strResult;
            }
            else
            {
                m_strHttpResponseDdJwt = string.Empty;
            }

            m_oLog.LogFileAddLineInfo($"IDP.HTTPGetDiscoveryDocumentUnsigned(): {bIsConnected}");


            return bIsConnected;
        }

        /// <summary>
        /// Second req to IDP:
        /// HTTP GET https://ref1-ets-ti-auth-server.kv-telematik.de/authorize?scope=Abrechnungsinformation&response_type=code&client_id=116117TerminserviceApp&state=7a6B1A8_dia3_XuHgqw1vLoGPV0&code_challenge=-fITIv2CTtG-EtVcnXKefk_ImnFH5bdy2wITMqlf4kA&code_challenge_method=S256&redirect_uri=https://localhost/      (url is REF1 - not production):
        /// 
        /// RESULT   
        /// </summary>
        private bool HttpGetInitAuthorisationCodeFlow()
        {
            m_oLog.LogFileAddLineInfo($"HttpGetInitAuthorisationCodeFlow() ...");

            bool bIsOk = false;

            if (string.IsNullOrEmpty(m_strUriAuthorizationCodeFlow)) throw new Exception($"UriAuthorizationCodeFlow is empty!");

            string strResult = string.Empty;
            int iStatusCode;
            string strStatusCode = string.Empty;
            bIsOk = HTTPGet(m_strUriAuthorizationCodeFlow, "GetInitAuthorisationCodeFlow", out strResult, out iStatusCode, out strStatusCode);
            if (bIsOk)
            {
                m_strHttpChallengeResponseString = strResult;
            }
            else
            {
                m_strHttpChallengeResponseString = string.Empty;
            }


            m_oLog.LogFileAddLineInfo($"HttpGetInitAuthorisationCodeFlow(): {bIsOk}");

            return bIsOk;
        }

        /// <summary>
        /// HTTP GET https://ref1-ets-ti-auth-server.kv-telematik.de/idpEnc/jwk.json
        /// </summary>
        /// <returns></returns>
        private bool GetPukIdpEnc()
        {
            m_oLog.LogFileAddLineInfo($"GetPukIdpEnc() ...");

            bool bIsOk = false;


            if (string.IsNullOrEmpty(m_strUriPukIdpEnc)) throw new Exception($"UriPukIdpEnc is empty!");

            string strResult = string.Empty;
            int iStatusCode;
            string strStatusCode = string.Empty;
            bIsOk = HTTPGet(m_strUriPukIdpEnc, "GetPukIdpEnc", out strResult, out iStatusCode, out strStatusCode);
            if (bIsOk)
            {
                m_strPukIdpEnc = strResult;

                // jwk-enc.json
                //{
                //    "x5c": ["MIIBIjCBygIUaQj4lLBGpD7tDZm/HUUYbX0/DC0wCgYIKoZIzj0EAwIwFDESMBAGA1UECwwJa3ZkaWdpdGFsMB4XDTI1MDIxMjE0MDkzMloXDTI4MDIxMjE0MDkzMlowFDESMBAGA1UECwwJa3ZkaWdpdGFsMFowFAYHKoZIzj0CAQYJKyQDAwIIAQEHA0IABAzlOktbeW2k1/w0oMe9Fum8IgXAAsIZtr77KNFBUZHZBZhs3RDAfdrGgFVdRxeNr1oTzPTOxr5X9zz3kIrJZQMwCgYIKoZIzj0EAwIDRwAwRAIgDxW4X4oJSQG2H/ACsVyNKt1oobptOhMH3MUZoPTdDesCIByif7yee0/Ut5LQV34FJ/Vctrosg5+b72OYC92LLbIc"],
                //    "use": "enc",
                //    "kid": "enc",
                //    "kty": "EC",
                //    "crv": "BP-256",
                //    "x": "DOU6S1t5baTX_DSgx70W6bwiBcACwhm2vvso0UFRkdk",
                //    "y": "BZhs3RDAfdrGgFVdRxeNr1oTzPTOxr5X9zz3kIrJZQM"
                //}

                m_oLog.LogFileAddLineInfo($" jwk-enc.json: {m_strPukIdpEnc}");
                m_oLog.FileWriteAllText("jwk-enc.json", m_strPukIdpEnc);
            }
            else
            {
                m_strPukIdpEnc = string.Empty;
            }

            m_oLog.LogFileAddLineInfo($"GetPukIdpEnc(): {bIsOk}");

            return bIsOk;
        }

        /// <summary>
        /// HTTP GET https://ref1-ets-ti-auth-server.kv-telematik.de/idpSig/jwk.json
        /// </summary>
        /// <returns></returns>
        private bool GetPukIdpSig()
        {
            m_oLog.LogFileAddLineInfo($"GetPukIdpSig() ...");

            bool bIsOk = false;


            if (string.IsNullOrEmpty(m_strUriPukIdpSig)) throw new Exception($"UriPukIdpSig is empty!");

            string strResult = string.Empty;
            int iStatusCode;
            string strStatusCode = string.Empty;
            bIsOk = HTTPGet(m_strUriPukIdpSig, "GetPukIdpSig", out strResult, out iStatusCode, out strStatusCode);
            if (bIsOk)
            {
                m_strPukIdpSig = strResult;

                //{
                //    "x5c": ["MIIBIjCBygIUGJVuGNGBCEPykE3Etqyp4pJrrowwCgYIKoZIzj0EAwIwFDESMBAGA1UECwwJa3ZkaWdpdGFsMB4XDTI1MDIxMjE0MDkzMVoXDTI4MDIxMjE0MDkzMVowFDESMBAGA1UECwwJa3ZkaWdpdGFsMFowFAYHKoZIzj0CAQYJKyQDAwIIAQEHA0IABImzOA+oJxNIUSC7aY5TcZcvPs4kPYH+vzHduU2+oGLIQ1E50TGZXVyeLUwU+efGZstUspyQK9dH71T/xeweV6AwCgYIKoZIzj0EAwIDRwAwRAIgbSUMRNUMO0Tw627H8Wa5EosPGgT/trZqYrKgM5CqfhgCIFZIiGIWhT8psG3BYVxgp030AOhah7OzoOMhR6V4A1m2"],
                //    "use": "sig",
                //    "kid": "sig",
                //    "kty": "EC",
                //    "crv": "BP-256",
                //    "x": "ibM4D6gnE0hRILtpjlNxly8-ziQ9gf6_Md25Tb6gYsg",
                //    "y": "Q1E50TGZXVyeLUwU-efGZstUspyQK9dH71T_xeweV6A"
                //}

                m_oLog.LogFileAddLineInfo($" jwk-sig.json: {m_strPukIdpSig}");
                m_oLog.FileWriteAllText("jwk-sig.json", m_strPukIdpSig);
            }
            else
            {
                m_strPukIdpSig = string.Empty;
            }

            m_oLog.LogFileAddLineInfo($"GetPukIdpSig(): {bIsOk}");

            return bIsOk;
        }

        /// <summary>
        /// HTTP POST https://ref1-ets-ti-auth-server.kv-telematik.de/authorize
        /// </summary>
        /// <returns></returns>
        private bool PostSignedCodeChallenge(HttpClient oHttpClient)
        {
            bool bIsOk = false;

 
            string strResult = string.Empty;
            int iStatusCode = 0;
            string strStatusCode = string.Empty;
            HttpResponseMessage oResponse = null;
            bIsOk = HTTPPost(m_strUriAuthorizationEndpoint, oHttpClient, m_oHttpContentPostSignedCodeChallenge, "PostSignedCodeChallenge", out strResult, out iStatusCode, out strStatusCode, out oResponse);
            if(bIsOk)
            {
                if(iStatusCode == 302)
                {
                    // Redirect
                    var values = HttpUtility.ParseQueryString(oResponse.Headers.Location.Query);
                    m_strTokenEndpointCode = values["code"];
                    m_oLog.LogFileAddLineInfo($"code: {m_strTokenEndpointCode}", true);

                    bIsOk = true;
                }
                else
                {
                    bIsOk = false;
                }
            }
            else
            {
                m_strTokenEndpointCode = string.Empty;

                bIsOk = false;
            }

            m_oLog.LogFileAddLineInfo($"PostSignedCodeChallenge(): {bIsOk}");

            return bIsOk;
        }

        private bool PostTokenEndpoint(HttpClient oHttpClient, out string strBearerToken)
        {
            bool bIsOk = false;

            strBearerToken = string.Empty;


            string strResult = string.Empty;
            int iStatusCode = 0;
            string strStatusCode = string.Empty;
            HttpResponseMessage oResponse = null;
            bIsOk = HTTPPost(m_strUriTokenEndpoint, oHttpClient, m_oHttpContentPostTokenEndpoint, "PostTokenEndpoint", out strResult, out iStatusCode, out strStatusCode, out oResponse);
            if (bIsOk)
            {
                if(iStatusCode == 200)
                {
                    //string responseString = responsetoken.Content.ReadAsStringAsync().Result;
                    //m_oLog.LogFileAddLineInfo($"Response body: {responseString}", true);
                    //AppendLoggingLine("erfolgreich", true);

                    var tokenResponseJson = JObject.Parse(strResult);
                    string access_token_encrypted = tokenResponseJson["access_token"].ToString();

                    var access_token = JObject.Parse(JWT.Decode(access_token_encrypted, m_byteRawAesTokenKey, JweAlgorithm.DIR, JweEncryption.A256GCM))["njwt"];

                    m_oLog.LogFileAddLineInfo($"BearerToken={access_token}", true);
                    strBearerToken = access_token.ToString();

                    bIsOk = true;
                }
                else
                {
                    bIsOk = false;
                }
            }
            else
            {
                m_strTokenEndpointCode = string.Empty;

                bIsOk = false;
            }

            m_oLog.LogFileAddLineInfo($"PostTokenEndpoint(): {bIsOk}");

            return bIsOk;
        }

        #endregion USECASES-HTTP-REQUESTS-IDP


        #region USECASES-HELPERS-IDP


        /// <summary>
        /// REF1:
        /// 
        /// uri_disc: 		        "https://ref1-ets-ti-auth-server.kv-telematik.de/.well-known/openid-configuration.unsigned"
        /// uri_puk_idp_enc: 	    "https://ref1-ets-ti-auth-server.kv-telematik.de/idpEnc/jwk.json"
        /// uri_puk_idp_sig: 	    "https://ref1-ets-ti-auth-server.kv-telematik.de/idpSig/jwk.json"
        /// authorization_endpoint:	"https://ref1-ets-ti-auth-server.kv-telematik.de/authorize"
        /// token_endpoint: 	    "https://ref1-ets-ti-auth-server.kv-telematik.de/token"
        /// </summary>
        private bool ResolveDiscoveryDocument()
        {
            m_oLog.LogFileAddLineInfo($"ResolveDiscoveryDocument() ...");

            bool bIsResolved = false;

            m_strUriDisc = string.Empty;
            m_strUriPukIdpSig = string.Empty;
            m_strUriPukIdpEnc = string.Empty;
            m_strUriAuthorizationEndpoint = string.Empty;
            m_strUriTokenEndpoint = string.Empty;

            try
            {
                if (string.IsNullOrEmpty(m_strHttpResponseDdJwt)) throw new Exception($"Failed to load discovery document from \"{m_strIdpServerUrlDD}\": Check connectivity and/or firewall settings!");

                var oDD = Newtonsoft.Json.Linq.JObject.Parse(m_strHttpResponseDdJwt);

                m_strUriDisc = (string)oDD["uri_disc"];
                m_strUriPukIdpSig = (string)oDD["uri_puk_idp_sig"];
                m_strUriPukIdpEnc = (string)oDD["uri_puk_idp_enc"];
                m_strUriAuthorizationEndpoint = (string)oDD["authorization_endpoint"];
                m_strUriTokenEndpoint = (string)oDD["token_endpoint"];

                var lExp = (long)oDD["exp"];
                m_dtExp = DateTimeOffset.FromUnixTimeSeconds(lExp).LocalDateTime;
                var lIat = (long)oDD["iat"];
                m_dtIat = DateTimeOffset.FromUnixTimeSeconds(lIat).LocalDateTime;
                m_oScopesSupported = (JArray)oDD["scopes_supported"];

                // Log DiscoveryDocument
                m_oLog.LogFileAddLineInfo($" uri_disc:               {m_strUriDisc}");
                m_oLog.LogFileAddLineInfo($" uri_puk_idp_sig:        {m_strUriPukIdpSig}");
                m_oLog.LogFileAddLineInfo($" uri_puk_idp_enc:        {m_strUriPukIdpEnc}");
                m_oLog.LogFileAddLineInfo($" authorization_endpoint: {m_strUriAuthorizationEndpoint}");
                m_oLog.LogFileAddLineInfo($" token_endpoint:         {m_strUriTokenEndpoint}");
                m_oLog.LogFileAddLineInfo($" iat: {lIat} - {m_dtIat}");
                m_oLog.LogFileAddLineInfo($" exp: {lExp} - {m_dtExp}");

                StringBuilder sbScopesSupported = new StringBuilder();
                foreach (var scope in m_oScopesSupported)
                {
                    sbScopesSupported.Append($" - {scope}");
                }
                // scopes_supported:  - abrechnungsinformation - terminsynchronisation - Vermittlungscode-anfordern - Abrechnungsinformation - Terminsynchronisation
                m_oLog.LogFileAddLineInfo($" scopes_supported: {sbScopesSupported}");

                bIsResolved = true;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $"Error resolving discovery document: {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                bIsResolved = false;
            }

            m_oLog.LogFileAddLineInfo($"ResolveDiscoveryDocument(): {bIsResolved}");


            return bIsResolved;
        }

        /// <summary>
        /// Prepare authorization call
        /// </summary>
        /// <param name="strScope">"Vermittlungscode-anfordern" or "Abrechnungsinformation"</param>
        private bool AuthorisationCodeFlowPrepare(string strScope)
        {
            m_oLog.LogFileAddLineInfo($"AuthorisationCodeFlowPrepare({strScope}) ...");

            bool bIsOk = false;

            m_strUriAuthorizationCodeFlow = string.Empty;

            try
            {
                string strState = CreateRandomState();
                if (string.IsNullOrEmpty(strState)) throw new Exception($"IDP.CreateRandowState() failed!");

                // gemSpec_IDP_Frontend A_20309
                m_strCodeVerifier = CreateRandomCodeverifier();
                if (string.IsNullOrEmpty(m_strCodeVerifier)) throw new Exception($"IDP.CreateRandomCodeverifier() failed!");

                string strCodeChallenge = Jose.Base64Url.Encode(CreateSha265HashAscii(m_strCodeVerifier));
                if (string.IsNullOrEmpty(strCodeChallenge)) throw new Exception($"Base64Url.Encode(CreateSha265HashAscii(strCodeVerifier) failed!");

                if(m_oScopesSupported.Any(x => x.Type == JTokenType.String && (string)x == strScope) == false) throw new Exception($"Anwendung (Scope={strScope}) is not supported!");      // check if a JArray in C# contains a specific string using LINQ

                // Initiieren des Authorisation Code Flows
                //
                // https://ref1-ets-ti-auth-server.kv-telematik.de/authorize?scope=Abrechnungsinformation&response_type=code&redirect_uri=https://localhost/&state=f1bQrZ4SEsiKCRV4VNqG&code_challenge_method=S256&client_id=116117TerminserviceApp&code_challenge=JvcJb54WkEm38N3U1IYQsP2Lqvv4Nx23D2mU7QePWEw
                //
                // https://ref1-ets-ti-auth-server.kv-telematik.de/authorize?
                //  scope=Abrechnungsinformation&
                //  response_type=code&
                //  redirect_uri=https://localhost/&
                //  state=f1bQrZ4SEsiKCRV4VNqG&                                     // Sollte bei jedem Start eines Autorisation Code Flows zufällig generiert werden.
                //  code_challenge_method=S256&
                //  client_id=116117TerminserviceApp&
                //  code_challenge=JvcJb54WkEm38N3U1IYQsP2Lqvv4Nx23D2mU7QePWEw      // 


                var httpParams = new[]
                {
                        ("scope", strScope),
                        ("response_type", "code"),
                        ("client_id", C_CLIENT_ID),
                        ("state", strState),
                        ("code_challenge", strCodeChallenge),
                        ("code_challenge_method", "S256"),
                        ("redirect_uri", C_URI_REDIRECT)
                    };

                if (string.IsNullOrEmpty(m_strUriAuthorizationEndpoint)) throw new Exception($"rUriAuthorizationEndpoint is empty!");
                m_strUriAuthorizationCodeFlow = $"{m_strUriAuthorizationEndpoint}?{string.Join("&", httpParams.Select(x => $"{x.Item1}={x.Item2}"))}";

                bIsOk = true;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $"Error prepare autorization uri: {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                bIsOk = false;
            }

            m_oLog.LogFileAddLineInfo($"AuthorisationCodeFlowPrepare({strScope}): {bIsOk}");


            return bIsOk;
        }

        private bool ResolveAuthorisationCodeFlow()
        {
            m_oLog.LogFileAddLineInfo($"ResolveAuthorisationCodeFlow() ...");

            bool bIsResolved = false;

            try
            {
                if (string.IsNullOrEmpty(m_strHttpChallengeResponseString)) throw new Exception($"Failed to load ChallengeResponseString from \"{m_strUriAuthorizationCodeFlow}\": Check connectivity and/or firewall settings!");

                //{
                //    "challenge": "eyJhbGciOiJCUDI1NlIxIiwidHlwIjoiSldUIiwia2lkIjoic2lnIn0.eyJpc3MiOiJodHRwczovL3JlZjEtZXRzLXRpLWF1dGgtc2VydmVyLmt2LXRlbGVtYXRpay5kZSIsInJlc3BvbnNlX3R5cGUiOiJjb2RlIiwic25jIjoiWzExMSwgODgsIDU1LCAxMDQsIDEwNywgMTEwLCA2NiwgNTIsIDEwMiwgMTAyLCA1MSwgMTE4LCA2NSwgNzgsIDc2LCAxMDYsIDEwOCwgMTE5LCA5NywgMTE5LCA4OSwgMTAyLCA1MywgMTAwLCA3OCwgNzUsIDg1LCAxMDQsIDEwNywgODksIDExNywgMTA2LCA1NCwgNzQsIDExOSwgOTksIDU2LCAxMjIsIDY5LCA1MSwgNTYsIDExNywgMTAzXSIsImNvZGVfY2hhbGxlbmdlX21ldGhvZCI6IlMyNTYiLCJ0b2tlbl90eXBlIjoiY2hhbGxlbmdlIiwiY2xpZW50X2lkIjoiMTE2MTE3VGVybWluc2VydmljZUFwcCIsImF1ZCI6WyJhYnJlY2hudW5nc2luZm9ybWF0aW9uLmt2dGcuZGUiXSwic2NvcGUiOiJBYnJlY2hudW5nc2luZm9ybWF0aW9uIiwic3RhdGUiOiJVY29pcEpwSUp1MGpLbmZnN0tyakVFd1BTYXciLCJyZWRpcmVjdF91cmkiOiJodHRwczovL2xvY2FsaG9zdC8iLCJleHAiOjE3NDc2Mzg1ODcsImlhdCI6MTc0NzYzODQwNywiY29kZV9jaGFsbGVuZ2UiOiJSVWttWURJZndsRmJDc0F2QVhrblN5M3RDc2pIeThwTml1MGhHb3c0RVFzIiwianRpIjoiM2YyZDA4OTA2NzUxOWRhNSJ9.oI82zOmDl7pk1UmPq1v1r0piueIE66lHsEmQu9PqfBSVl6oTegy-TDoKw1dQLGN7arVtX27uEvwNZn0TxwgOew",
                //    "user_consent": {
                //        "requested_scopes": {
                //            "Abrechnungsinformation": "Anfordern von Abrechnungsinformationen beim 116 117 Terminservice"
                //        },
                //        "requested_claims": {
                //            "nbsnrs": "Alle NBSNRS zur BSNR.",
                //            "x5c": "Zertifikat der SMC-B bzw. SMB.",
                //            "bsnr": "Die BSNR der Einrichtung."
                //        }
                //    }
                //}

                var jsonChallengeResponseJson = Newtonsoft.Json.Linq.JObject.Parse(m_strHttpChallengeResponseString);
                m_strChallengeToken = (string)jsonChallengeResponseJson["challenge"];                          // der als Json Web Token codierter challenge_token
                m_oLog.LogFileAddLineInfo($" challenge: {m_strChallengeToken}");

                bIsResolved = true;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $"Error resolving AuthorisationCodeFlow: {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                bIsResolved = false;
            }

            m_oLog.LogFileAddLineInfo($"ResolveAuthorisationCodeFlow(): {bIsResolved}");


            return bIsResolved;
        }

        private bool DecodePukIdpSig()
        {
            m_oLog.LogFileAddLineInfo($"DecodePukIdpSig() ...");

            bool bIsOk = false;

            try
            {
                var sigCertString = Newtonsoft.Json.Linq.JObject.Parse(m_strPukIdpSig)["x5c"].FirstOrDefault()?.ToString();
                // ReSharper disable once AssignNullToNotNullAttribute
                var idp_token_sig_cert = new X509Certificate2(Encoding.UTF8.GetBytes(sigCertString));
                Org.BouncyCastle.Crypto.Parameters.ECPublicKeyParameters zertKey2 = RetrievePubKeyFromCert(idp_token_sig_cert);

                // Verify signature of challengeJwtString using public key of IDP certificate (uri_puk_idp_sig)
                m_strChallengeTokenDecoded = Jose.JWT.Decode(m_strChallengeToken, zertKey2, Jose.JwsAlgorithm.ES256, new Jose.JwtSettings().RegisterJws(Jose.JwsAlgorithm.ES256, new BrainPoolP256r1JwsAlgorithm()).RegisterJwsAlias("BP256R1", Jose.JwsAlgorithm.ES256));

                if (string.IsNullOrEmpty(m_strChallengeTokenDecoded)) throw new Exception($"DecodedChallengeToken is empty!");

                m_oChallengeTokenDecoded = Newtonsoft.Json.Linq.JObject.Parse(m_strChallengeTokenDecoded);

                //{
                //    "iss": "https://ref1-ets-ti-auth-server.kv-telematik.de",
                //    "response_type": "code",
                //    "snc": "[101, 89, 112, 87, 70, 108, 89, 111, 119, 97, 77, 97, 99, 100, 76, 78, 54, 122, 70, 98, 71, 51, 78, 118, 72, 82, 89, 114, 87, 54, 75, 49, 52, 83, 85, 89, 112, 85, 83, 66, 108, 86, 115]",
                //    "code_challenge_method": "S256",
                //    "token_type": "challenge",
                //    "client_id": "116117TerminserviceApp",
                //    "aud": ["abrechnungsinformation.kvtg.de"],
                //    "scope": "Abrechnungsinformation",
                //    "state": "jr4EkJ2DTrQot3TiDKdBxHb_oxw",
                //    "redirect_uri": "https://localhost/",
                //    "exp": 1747646352,
                //    "iat": 1747646172,
                //    "code_challenge": "qFTLXkEASA6Pz2c59czFeXg8wcvd0Hs-2bDjRCHEJ_E",
                //    "jti": "7f82b40293b25943"
                //}

                m_strChallengeTokenCodeChallenge = (string)m_oChallengeTokenDecoded["code_challenge"];
                m_oLog.LogFileAddLineInfo($" code_challenge: {m_strChallengeTokenCodeChallenge}");

                m_oLog.FileWriteAllText("decodierter_challenge_token.json", m_strChallengeTokenDecoded);

                if (string.IsNullOrEmpty(m_strChallengeTokenCodeChallenge)) throw new Exception($"code_challenge is empty!");

                bIsOk = true;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = string.Format(" Exception (IDP.DecodePukIdpSig): {0}", ex.Message);
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                bIsOk = false;
            }

            m_oLog.LogFileAddLineInfo($"DecodePukIdpSig(): {bIsOk}");

            return bIsOk;
        }

        private bool SignCodeChallenge(X509Certificate2 certSmcb, bool bIsCertEcc)
        {
            m_oLog.LogFileAddLineInfo($"SignCodeChallenge() ...");

            bool bIsSigned = false;

            try
            {
                if (string.IsNullOrEmpty(m_strChallengeTokenCodeChallenge)) throw new Exception($"Failed to load code challenge!");


                // create nested jwt with challenge included
                //AppendLoggingLine($"=> SMC.AUT={certSmcb.SubjectName.Name}, alg: {certSmcb.GetKeyAlgorithm()}", false);
                var smcbAuthCertB64 = new[] { Convert.ToBase64String(certSmcb.RawData) };
                //AppendLoggingLine($"=> SMC.AUT(b64)={smcbAuthCertB64[0]}", true);

                var njwtHeadersB64 = Base64Url.Encode(Encoding.UTF8.GetBytes(new JObject
                {
                    ["alg"] = bIsCertEcc ? "BP256R1" : "PS256",         // ECC oder RSA?
                    ["cty"] = "NJWT",
                    ["x5c"] = JToken.FromObject(smcbAuthCertB64),
                }.ToString(Formatting.None)));

                var njwtPayloadB64 = Base64Url.Encode(Encoding.UTF8.GetBytes(new JObject { ["njwt"] = m_strChallengeToken }.ToString(Formatting.None)));

                // sign nested jwt
                var headerPayloadString = $"{njwtHeadersB64}.{njwtPayloadB64}";
                var bytesToHash = Encoding.UTF8.GetBytes(headerPayloadString);

                // Create a SHA256  
                var digester = new SHA256Managed();
                byte[] sha265Digest = digester.ComputeHash(bytesToHash);

                // sign
                byte[] byteSignature = null;
                if (bIsCertEcc)
                {
                    byte[] byteSignatureAsn1 = m_oKonnektor.AuthenticateExternalEcc(sha265Digest);
                    byteSignature = SignatureConverter.ConvertDerToConcatenated(byteSignatureAsn1, 256);
                }
                else
                {
                    byteSignature = m_oKonnektor.AuthenticateExternal(sha265Digest);
                }

                var jws = $"{njwtHeadersB64}.{njwtPayloadB64}.{Base64Url.Encode(byteSignature)}";
                //AppendLoggingLine($"to Hash (String) : {headerPayloadString}", true);
                //AppendLoggingLine($"njwtHash ={VAU.ByteArrayToHexString(sha265Digest)}", true);
                //AppendLoggingLine($"jws: {jws}", true);

                // create JWE with JWS nested
                m_strJwePayloadJson = new JObject { ["njwt"] = jws }.ToString(Formatting.None);
                //m_oLog.LogFileAddLineInfo($"jwe with nested jws: {m_strJwePayloadJson}");
                //AppendLoggingLine($"jwePayload: {jwePayloadJson}", true);


                m_oIdpEncKeyPublic = ResolveIdpPubKey(m_strPukIdpEnc);


                bIsSigned = true;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $"Error signing code challenge: {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                bIsSigned = false;
            }

            m_oLog.LogFileAddLineInfo($"SignCodeChallenge(): {bIsSigned}");


            return bIsSigned;
        }

        private bool PreparePostSignedCodeChallenge(out HttpClient oHttpClient)
        {
            bool bIsOk = false;

            oHttpClient = null;

            try
            {
                if (m_oIdpEncKeyPublic == null) throw new Exception($"Failed to decode IDP pub key enc!");

                m_lExpChallengeToken = long.Parse(m_oChallengeTokenDecoded["exp"].ToString());
                m_oLog.LogFileAddLineInfo($"ExpChallengeToken={m_lExpChallengeToken} => {DateTimeOffset.FromUnixTimeSeconds(m_lExpChallengeToken).LocalDateTime}");

                string jwe = JWT.Encode(m_strJwePayloadJson, m_oIdpEncKeyPublic, JweAlgorithm.ECDH_ES, JweEncryption.A256GCM,
                    settings: new JwtSettings().RegisterJwa(JweAlgorithm.ECDH_ES, new BrainPoolP256r1EcdhKeyManagement()),
                    extraHeaders: new Dictionary<string, object> {
                    {"exp", m_lExpChallengeToken},
                    {"cty", "NJWT"},
                    });
                //AppendLoggingLine($"jwe: {jwe}", true);

                // Content-Type: application/x-www-form-urlencoded'
                // signed_challenge=JWE
                var handler = new HttpClientHandler // don't follow redirect
                {
                    AllowAutoRedirect = false
                };
                oHttpClient = new HttpClient(handler);
                //var cl3 = new HttpClient(handler);

 (*)            m_oHttpContentPostSignedCodeChallenge = new FormUrlEncodedContent(new[] { new KeyValuePair<string, string>("signed_challenge", jwe) });

                if (m_oHttpContentPostSignedCodeChallenge == null) throw new Exception("Creation of HttpContentPostSignedCodeChallenge failed!");

                bIsOk = true;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $"Error PreparePostSignedCodeChallenge: {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                bIsOk = false;
            }

            m_oLog.LogFileAddLineInfo($"PreparePostSignedCodeChallenge(): {bIsOk}");


            return bIsOk;
        }

        private bool PreparePostTokenEndpoint(out HttpClient oHttpClient)
        {
            bool bIsOk = false;

            oHttpClient = null;

            try
            {
                //und nun das Token abholen

                //JWE, welches den code_verifier sowie den token_key enthält. Dies ist ein AES-Schlüssel welcher vom Server zur Verschlüsselung der Token-Rückgaben verwendet wird.
                //Enthalten ist der code_verifier (der zu dem code_challenge-Wert aus der initialen Anfrage passen muss) sowie der token_key. Dies ist ein vom Client zufällig gewürfelter AES256-Schlüssel in Base64-URL-Encoding. Der Server benutzt diesen Schlüssel zur Chiffrierung der beiden Token-Rückgaben in der Response (ID- und Access-Token).
                // ReSharper disable once RedundantAssignment
                //byte[] m_byteRawAesTokenKey = new byte[32];
                m_oRandom.NextBytes(m_byteRawAesTokenKey);
                string tokenaeskey = Base64Url.Encode(m_byteRawAesTokenKey);

                var key_verifier_jwe_payload_json = new JObject { ["token_key"] = tokenaeskey, ["code_verifier"] = m_strCodeVerifier }.ToString(Formatting.None);
                //AppendLoggingLine($"key_verifier_jwe_payload_json: {key_verifier_jwe_payload_json}", true);

                string key_verifier_jwe = JWT.Encode(key_verifier_jwe_payload_json, m_oIdpEncKeyPublic, JweAlgorithm.ECDH_ES,
                    JweEncryption.A256GCM,
                    settings: new JwtSettings().RegisterJwa(JweAlgorithm.ECDH_ES, new BrainPoolP256r1EcdhKeyManagement()),
                    extraHeaders: new Dictionary<string, object> {
                    {"exp", m_lExpChallengeToken},
                    {"cty", "NJWT"},
                    });

                //z.B. http://url.des.idp/token
                oHttpClient = new HttpClient();
                //var cltoken = new HttpClient();
                oHttpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));


                //gemSpec_IDP_Frontend A_20529
                m_oHttpContentPostTokenEndpoint = new FormUrlEncodedContent(new[] {
                    new KeyValuePair<string, string>("key_verifier", key_verifier_jwe),
                    new KeyValuePair<string, string>("code", m_strTokenEndpointCode),
                    new KeyValuePair<string, string>("grant_type", "authorization_code"),
                    new KeyValuePair<string, string>("redirect_uri", C_URI_REDIRECT),
                    new KeyValuePair<string, string>("client_id", C_CLIENT_ID),
                    });

                if (m_oHttpContentPostTokenEndpoint == null) throw new Exception("Creation of HttpContentPostTokenEndpoint failed!");

                bIsOk = true;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $"Error PreparePostTokenEndpoint: {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                bIsOk = false;
            }

            m_oLog.LogFileAddLineInfo($"PreparePostTokenEndpoint(): {bIsOk}");


            return bIsOk;
        }

        #endregion USECASES-HELPERS-IDP


        #region HELPERS-IDP

        private bool HTTPGet(string strUrl, string strMethodName, out string strResult, out int iStatusCode, out string strStatusCode)
        {
            bool bIsOk = false;

            strResult = string.Empty;
            iStatusCode = 0;
            strStatusCode = string.Empty;

            m_oLog.LogFileAddLineInfo($"{strMethodName}() ...");

            try
            {
                if (string.IsNullOrEmpty(strUrl)) throw new Exception("Url is empty!");

                m_oLog.LogFileHttpRequest($"{strMethodName}", "GET", strUrl, m_oHttpClientDefault.DefaultRequestHeaders, null, "");

                m_oLog.LogFileAddLineInfo($" connect \"{strUrl}\" (timeout={m_oHttpClientDefault.Timeout.TotalSeconds}s) ...");
                var response = m_oHttpClientDefault.GetAsync(strUrl).Result;
                m_oLog.LogFileHttpResponse($"{strMethodName}", response);

                iStatusCode = (int)response.StatusCode;
                strStatusCode = response.StatusCode.ToString();
                m_oLog.LogFileAddLineInfo($" connect \"{strUrl}\": {iStatusCode} ({strStatusCode})");

                if (response.IsSuccessStatusCode)
                {
                    strResult = response.Content.ReadAsStringAsync().Result;
                    bIsOk = true;
                }
                else
                {
                    m_strLastErrorExpert = $"GET {strUrl}\r\n{strResult}\r\n\r\n(HTTP-response: {iStatusCode} ({strStatusCode})\r\n\r\n{response.Content.ReadAsStringAsync().Result}";
                    //m_strLastErrorUserDE = $"Fehler beim Verbindungsaufbau. Infos für Systemadministrator: {m_strLastErrorExpert}";
                    bIsOk = false;
                }
            }
            catch (ArgumentNullException anex)
            {
                m_strLastErrorExpert = anex.Message;
                m_oLog.LogFileAddLineError($" ArgumentNullException HTTP GET \"{strUrl}\": {m_strLastErrorExpert}");

                strResult = string.Empty;
            }
            catch (HttpRequestException hrex)
            {
                m_strLastErrorExpert = hrex.Message;
                m_oLog.LogFileAddLineError($" HttpRequestException HTTP GET \"{strUrl}\": {m_strLastErrorExpert}");
                strResult = string.Empty;
            }
            catch (WebException wex)
            {
                m_strLastErrorExpert = $" WebException HTTP GET \"{strUrl}\": {wex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                strResult = string.Empty;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $" Exception HTTP GET \"{strUrl}\": {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                strResult = string.Empty;
            }

            m_oLog.LogFileAddLineInfo($"{strMethodName}(): {bIsOk}");


            return bIsOk;
        }

        private bool HTTPPost(string strUrl, HttpClient oHttpClient, HttpContent oHttpContent, string strMethodName, out string strResult, out int iStatusCode, out string strStatusCode, out HttpResponseMessage oResponse)
        {
            bool bIsOk = false;

            strResult = string.Empty;
            iStatusCode = 0;
            strStatusCode = string.Empty;
            oResponse = null;

            m_oLog.LogFileAddLineInfo($"{strMethodName}() ...");

            try
            {
                if (string.IsNullOrEmpty(strUrl)) throw new Exception("Url is empty!");

                m_oLog.LogFileHttpRequest($"{strMethodName}", "POST", strUrl, oHttpClient.DefaultRequestHeaders, null, "");

                m_oLog.LogFileAddLineInfo($" connect \"{strUrl}\" (timeout={oHttpClient.Timeout.TotalSeconds}s) ...");
                oResponse = oHttpClient.PostAsync(strUrl, oHttpContent).Result;
                m_oLog.LogFileHttpResponse($"{strMethodName}", oResponse);

                iStatusCode = (int)oResponse.StatusCode;
                strStatusCode = oResponse.StatusCode.ToString();
                m_oLog.LogFileAddLineInfo($" connect \"{strUrl}\": {iStatusCode} ({strStatusCode})");

                strResult = oResponse.Content.ReadAsStringAsync().Result;
                bIsOk = true;
            }
            catch (ArgumentNullException anex)
            {
                m_strLastErrorExpert = anex.Message;
                m_oLog.LogFileAddLineError($" ArgumentNullException HTTP POST \"{strUrl}\": {m_strLastErrorExpert}");

                strResult = string.Empty;
            }
            catch (HttpRequestException hrex)
            {
                m_strLastErrorExpert = hrex.Message;
                m_oLog.LogFileAddLineError($" HttpRequestException HTTP POST \"{strUrl}\": {m_strLastErrorExpert}");
                strResult = string.Empty;
            }
            catch (WebException wex)
            {
                m_strLastErrorExpert = $" WebException HTTP POST \"{strUrl}\": {wex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                strResult = string.Empty;
            }
            catch (Exception ex)
            {
                m_strLastErrorExpert = $" Exception HTTP POST \"{strUrl}\": {ex.Message}";
                m_oLog.LogFileAddLineError(m_strLastErrorExpert);
                strResult = string.Empty;
            }

            m_oLog.LogFileAddLineInfo($"{strMethodName}(): {bIsOk}");


            return bIsOk;
        }

        private string CreateRandomState()
        {
            // Der state - Parameter wird vom Client zufällig generiert, um CSRF zu verhindern. Indem der Server mit diesem Wert antwortet, werden Redirects legitimiert. state kann eine maximale Länge von 512 Zeichen haben.
            return Jose.Base64Url.Encode(m_oRandom.GenerateSeed(20));
        }

        private string CreateRandomCodeverifier()
        {
            // https://tools.ietf.org/html/rfc7636#section-4.1
            return Jose.Base64Url.Encode(m_oRandom.GenerateSeed(60));
        }

        private static byte[] CreateSha265HashAscii(string codeVerifier)
        {
            var z = new Org.BouncyCastle.Crypto.Digests.Sha256Digest();
            var bytes = Encoding.ASCII.GetBytes(codeVerifier);
            z.BlockUpdate(bytes, 0, bytes.Length);
            var sha265Hash = new byte[32];
            var f = z.DoFinal(sha265Hash, 0); //f==32
            if (f != 32)
            {
                throw new ArgumentException("Fehler bei Sha265HashAscii -> muss 32 sein");
            }

            return sha265Hash;
        }

        ECPublicKeyParameters ResolveIdpPubKey(string strJson)
        {
            var idpEncKeyJson = JObject.Parse(strJson);
            //AppendLoggingLine($"idpEncKeyJson: {idpEncKeyJson["x"]} {idpEncKeyJson["y"]}", true);

            X9ECParameters x9EC = ECNamedCurveTable.GetByOid(TeleTrusTObjectIdentifiers.BrainpoolP256R1);
            ECDomainParameters domainParams = new ECDomainParameters(x9EC.Curve, x9EC.G, x9EC.N, x9EC.H, x9EC.GetSeed());

            var x = new BigInteger(1, Base64Url.Decode(idpEncKeyJson["x"].ToString()));
            var y = new BigInteger(1, Base64Url.Decode(idpEncKeyJson["y"].ToString()));
            var idpEcPoint = domainParams.Curve.CreatePoint(x, y);

            return new ECPublicKeyParameters(idpEcPoint, domainParams);
        }

        internal static Org.BouncyCastle.Crypto.Parameters.ECPublicKeyParameters RetrievePubKeyFromCert(X509Certificate2 cert)
        {
            return new Org.BouncyCastle.X509.X509CertificateParser().ReadCertificate(cert.GetRawCertData()).GetPublicKey() as Org.BouncyCastle.Crypto.Parameters.ECPublicKeyParameters;
        }

        internal class BrainPoolP256r1EcdhKeyManagement : IKeyManagement
        {
            private readonly X9ECParameters _brainpoolP256R1 = ECNamedCurveTable.GetByOid(TeleTrusTObjectIdentifiers.BrainpoolP256R1);
            const string BCRYPT_ALG_ID_HEADER = "alg";
            const string CRV = "BP-256";

            public virtual byte[][] WrapNewKey(int cekSizeBits, object externalPubKey, IDictionary<string, object> header)
            {
                var cek = NewKey(cekSizeBits, externalPubKey, header);
                var encryptedCek = Jose.Arrays.Empty;
                return new[] { cek, encryptedCek };
            }

            private byte[] NewKey(int keyLength, object externalPubKey, IDictionary<string, object> header)
            {
                // create ECDH-ES content encryption key
                // generate keypair for ECDH
                SecureRandom rnd = new SecureRandom();
                var keyGen = new ECKeyPairGenerator();
                var domainParams = new ECDomainParameters(_brainpoolP256R1.Curve, _brainpoolP256R1.G, _brainpoolP256R1.N, _brainpoolP256R1.H);
                var genParam = new ECKeyGenerationParameters(domainParams, rnd);
                keyGen.Init(genParam);
                var ecdhKeyPair = keyGen.GenerateKeyPair();
                var ephemeralPubkey = (ECPublicKeyParameters)ecdhKeyPair.Public;
                var ephemeralPrvKey = (ECPrivateKeyParameters)ecdhKeyPair.Private;

                header["epk"] = new Dictionary<string, object>
                {
                    ["kty"] = "EC",
                    ["x"] = Base64Url.Encode(ephemeralPubkey.Q.XCoord.GetEncoded()),
                    ["y"] = Base64Url.Encode(ephemeralPubkey.Q.YCoord.GetEncoded()),
                    ["crv"] = CRV
                };

                var deriveKey = DeriveKey(header, keyLength, externalPubKey as ECPublicKeyParameters, ephemeralPrvKey);
                //Console.Out.WriteLine($"dervied key (cek): {VAU.ByteArrayToHexString(deriveKey)}");

                return deriveKey;
            }

            static byte[] DeriveKey(IDictionary<string, object> header, int cekSizeBits, ECPublicKeyParameters externalPublicKey, ECPrivateKeyParameters ephemeralPrvKey)
            {
                var z = EcdhKeyAgreementZ(externalPublicKey, ephemeralPrvKey);

                var kdfGen = new ConcatenationKdfGenerator(new Sha256Digest());

                byte[] algId = Encoding.ASCII.GetBytes(header["enc"].ToString());
                byte[] apu = header.ContainsKey("apu") ? Base64Url.Decode((string)header["apu"]) : Jose.Arrays.Empty;
                byte[] apv = header.ContainsKey("apv") ? Base64Url.Decode((string)header["apv"]) : Jose.Arrays.Empty;
                byte[] kdl = CalcBeLengthArray(cekSizeBits);

                var otherInfo = Jose.Arrays.Concat(PrependLength(algId), PrependLength(apu), PrependLength(apv), kdl);

                kdfGen.Init(new KdfParameters(z, otherInfo));
                byte[] secretKeyBytes = new byte[32];
                kdfGen.GenerateBytes(secretKeyBytes, 0, secretKeyBytes.Length);
                return secretKeyBytes;
            }

            static byte[] EcdhKeyAgreementZ(ECPublicKeyParameters externalPublicKey, ECPrivateKeyParameters ephemeralPrvKey)
            {
                var ecdh = new ECDHBasicAgreement();
                ecdh.Init(ephemeralPrvKey);

                var z = ecdh.CalculateAgreement(externalPublicKey);
                return BigIntegers.AsUnsignedByteArray(32, z);
            }

            static byte[] CalcBeLengthArray(int length)
            {
                var l = BitConverter.GetBytes(length);
                if (BitConverter.IsLittleEndian)
                {
                    Array.Reverse(l);
                }
                return l;
            }

            static byte[] PrependLength(byte[] data)
            {
                return Jose.Arrays.Concat(CalcBeLengthArray(data.Length), data);
            }

            public virtual byte[] WrapKey(byte[] cek, object key, IDictionary<string, object> header)
            {
                throw new JoseException("(Direct) ECDH-ES key management cannot use existing CEK.");
            }

            public virtual byte[] Unwrap(byte[] encryptedCek, object privateKey, int cekSizeBits, IDictionary<string, object> header)
            {
                Ensure.Contains(header, new[] { "epk" }, "EcdhKeyManagement algorithm expects 'epk' key param in JWT header, but was not found");
                Ensure.Contains(header, new[] { BCRYPT_ALG_ID_HEADER },
                    "EcdhKeyManagement algorithm expects 'enc' header to be present in JWT header, but was not found");

                var epk = (IDictionary<string, object>)header["epk"];

                Ensure.Contains(epk, new[] { "x", "y", "crv" },
                    "EcdhKeyManagement algorithm expects 'epk' key to contain 'x','y' and 'crv' fields.");

                var x = new BigInteger(Base64Url.Decode(epk["x"].ToString()));
                var y = new BigInteger(Base64Url.Decode(epk["y"].ToString()));
                var externalPubKeyPoint = _brainpoolP256R1.Curve.CreatePoint(x, y);

                var domainParams = new ECDomainParameters(_brainpoolP256R1.Curve, _brainpoolP256R1.G, _brainpoolP256R1.N, _brainpoolP256R1.H);
                var externalPubKey = new ECPublicKeyParameters(externalPubKeyPoint, domainParams);

                return DeriveKey(header, cekSizeBits, externalPubKey, privateKey as ECPrivateKeyParameters);
            }
        }

        internal class BrainPoolP256r1JwsAlgorithm : IJwsAlgorithm
        {
            public byte[] Sign(byte[] securedInput, object key)
            {
                throw new NotImplementedException();
            }

            public bool Verify(byte[] signature, byte[] securedInput, object key)
            {
                if (key is not ECPublicKeyParameters publicKey)
                {
                    throw new ArgumentException("key must be ECPublicKeyParameters");
                }

                ISigner signer = SignerUtilities.GetSigner(X9ObjectIdentifiers.ECDsaWithSha256.Id);
                signer.Init(false, publicKey);
                signer.BlockUpdate(securedInput, 0, securedInput.Length);

                var derSignature = new DerSequence(
                        // first 32 bytes is "r" number
                        new DerInteger(new BigInteger(1, signature.Take(32).ToArray())),
                        // last 32 bytes is "s" number
                        new DerInteger(new BigInteger(1, signature.Skip(32).ToArray())))
                    .GetDerEncoded();

                var verifySignature = signer.VerifySignature(derSignature);
                return verifySignature;
            }
        }

        #endregion HELPERS-IDP
    }
}
