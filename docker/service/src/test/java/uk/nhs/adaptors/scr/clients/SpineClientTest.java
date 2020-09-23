package uk.nhs.adaptors.scr.clients;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.nhs.adaptors.scr.config.SpineConfiguration;
import uk.nhs.adaptors.scr.exceptions.NoScrResultException;
import uk.nhs.adaptors.scr.exceptions.ScrBaseException;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpineClientTest {

    private static final String SPINE_URL = "https://spine";
    private static final String CONTENT_LOCATION = "https://spine/content-location";
    private static final String ACS_ENDPOINT = "/acs";
    private static final String SCR_ENDPOINT = "/scr";
    private static final String RESPONSE_BODY = "some_body";
    private static final String ACS_REQUEST_BODY = "some_acs_body";

    @Mock
    private SpineConfiguration spineConfiguration;
    @Mock
    private SpineHttpClient spineHttpClient;

    @InjectMocks
    private SpineClient spineClient;

    @BeforeEach
    void setUp() {
    }

    @Test
    void whenSendingAcsExpectResult() {
        when(spineConfiguration.getUrl()).thenReturn(SPINE_URL);
        when(spineConfiguration.getAcsEndpoint()).thenReturn(ACS_ENDPOINT);
        when(spineHttpClient.sendRequest(any(HttpPost.class)))
            .thenReturn(new SpineHttpClient.Response(HttpStatus.OK.value(), new Header[0], RESPONSE_BODY));

        var response = spineClient.sendAcsData(ACS_REQUEST_BODY);

        var httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        verify(spineHttpClient).sendRequest(httpPostArgumentCaptor.capture());

        var httpPost = httpPostArgumentCaptor.getValue();
        assertThat(httpPost.getURI().toString()).isEqualTo(SPINE_URL + ACS_ENDPOINT);

        assertThat(response.getBody()).isEqualTo(RESPONSE_BODY);
    }

    @Test
    void whenSendingScrReturns202ExpectResult() {
        when(spineConfiguration.getUrl()).thenReturn(SPINE_URL);
        when(spineConfiguration.getScrEndpoint()).thenReturn(SCR_ENDPOINT);
        var headers = new Header[] {
            new BasicHeader("Header-Name", "headerValue")
        };
        when(spineHttpClient.sendRequest(any(HttpPost.class)))
            .thenReturn(new SpineHttpClient.Response(HttpStatus.ACCEPTED.value(), headers, RESPONSE_BODY));

        var response = spineClient.sendScrData(ACS_REQUEST_BODY);

        var httpPostArgumentCaptor = ArgumentCaptor.forClass(HttpPost.class);
        verify(spineHttpClient).sendRequest(httpPostArgumentCaptor.capture());

        var httpPost = httpPostArgumentCaptor.getValue();
        assertThat(httpPost.getURI().toString()).isEqualTo(SPINE_URL + SCR_ENDPOINT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(response.getHeaders()).isEqualTo(headers);
        assertThat(response.getBody()).isEqualTo(RESPONSE_BODY);
    }

    @Test
    void whenSendingScrReturnsNon202ExpectResult() {
        when(spineConfiguration.getUrl()).thenReturn(SPINE_URL);
        when(spineConfiguration.getScrEndpoint()).thenReturn(SCR_ENDPOINT);
        when(spineHttpClient.sendRequest(any(HttpPost.class)))
            .thenReturn(new SpineHttpClient.Response(HttpStatus.OK.value(), new Header[0], RESPONSE_BODY));

        assertThatThrownBy(() -> spineClient.sendScrData(ACS_REQUEST_BODY))
            .isExactlyInstanceOf(ScrBaseException.class);
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void whenGetScrProcessingResultReturnsWithOneRetryWithinTimeExpectResult() {
        when(spineConfiguration.getScrResultRepeatTimeout()).thenReturn(500L);

        var postResponseHeaders = new Header[] {
            new BasicHeader("Retry-After", "50")
        };

        when(spineHttpClient.sendRequest(any()))
            .thenReturn(new SpineHttpClient.Response(HttpStatus.ACCEPTED.value(), postResponseHeaders, null))
            .thenReturn(new SpineHttpClient.Response(HttpStatus.OK.value(), new Header[0], RESPONSE_BODY));

        var result = spineClient.getScrProcessingResult(CONTENT_LOCATION, 50);

        var requestArgumentCaptor = ArgumentCaptor.forClass(HttpGet.class);
        verify(spineHttpClient, times(2)).sendRequest(requestArgumentCaptor.capture());

        assertRequestUri(requestArgumentCaptor.getAllValues());
        assertThat(result).isEqualTo(RESPONSE_BODY);
    }

    @Test
    @SuppressWarnings("checkstyle:MagicNumber")
    void whenGetScrProcessingResultReachesRepeatTimeoutExpectException() {
        when(spineConfiguration.getScrResultRepeatTimeout()).thenReturn(500L);

        var postResponseHeaders = new Header[] {
            new BasicHeader("Retry-After", String.valueOf(200))
        };

        when(spineHttpClient.sendRequest(any()))
            .thenReturn(new SpineHttpClient.Response(HttpStatus.ACCEPTED.value(), postResponseHeaders, null));

        assertThatThrownBy(() -> spineClient.getScrProcessingResult(CONTENT_LOCATION, 100))
            .isExactlyInstanceOf(NoScrResultException.class)
            .hasMessage("SCR polling yield no result");

        var requestArgumentCaptor = ArgumentCaptor.forClass(HttpGet.class);
        verify(spineHttpClient, atLeast(2)).sendRequest(requestArgumentCaptor.capture());
        assertRequestUri(requestArgumentCaptor.getAllValues());
    }

    private void assertRequestUri(List<HttpGet> requests) {
        assertThat(requests.stream()
            .map(HttpRequestBase::getURI)
            .map(URI::toString)
            .collect(Collectors.toList()))
            .allMatch(url -> url.equals(CONTENT_LOCATION));
    }
}
