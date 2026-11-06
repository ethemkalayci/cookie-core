package be.cookies.studio.core.update;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongConsumer;

public final class ReleaseHttp {
    record Response(int status,Map<String,String> headers,InputStream body) {}
    @FunctionalInterface interface Transport { Response get(URI uri) throws Exception; }
    private final Transport transport;
    public ReleaseHttp() {
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        transport=uri->{
            var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent","CookieCore-Updater/1").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            var headers=new HashMap<String,String>();
            response.headers().firstValue("Location").ifPresent(v->headers.put("Location",v));
            response.headers().firstValue("Content-Length").ifPresent(v->headers.put("Content-Length",v));
            return new Response(response.statusCode(),headers,response.body());
        };
    }
    ReleaseHttp(Transport transport) {this.transport=transport;}
    static void validate(URI uri) {
        if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getFragment()!=null)
            throw new SecurityException("URL HTTPS requise");
        boolean github="github.com".equals(uri.getHost()) && uri.getPath().startsWith("/ethemkalayci/cookie-core/releases/");
        boolean asset="release-assets.githubusercontent.com".equals(uri.getHost()) && uri.getPath().startsWith("/github-production-release-asset/");
        if(!github && !asset)throw new SecurityException("Hôte ou chemin interdit");
    }
    public byte[] bytes(URI uri,int limit) throws Exception {
        var out=new ByteArrayOutputStream(); transfer(uri,out,limit,n->{}); return out.toByteArray();
    }
    public void download(URI uri,Path target,long limit,LongConsumer progress) throws Exception {
        boolean created=false;
        try {
            var stream=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW);created=true;
            try(var out=stream){transfer(uri,out,limit,progress);}
        } catch(Exception e){if(created)Files.deleteIfExists(target);throw e;}
    }
    private void transfer(URI uri,OutputStream out,long limit,LongConsumer progress) throws Exception {
        for(int redirects=0;redirects<5;redirects++) {
            validate(uri);var response=transport.get(uri);
            try(var in=response.body()) {
                if(response.status()>=300 && response.status()<400) {uri=uri.resolve(Objects.requireNonNull(response.headers().get("Location")));continue;}
                if(response.status()!=200)throw new IOException("HTTP "+response.status());
                long expected=Long.parseLong(response.headers().getOrDefault("Content-Length","-1"));
                if(expected>limit)throw new IOException("Réponse trop volumineuse");
                var timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
                var deadline=timer.schedule(()->{try{in.close();}catch(IOException ignored){}},180,TimeUnit.SECONDS);
                try {
                    byte[] buffer=new byte[65536];long total=0;
                    for(int n;(n=in.read(buffer))!=-1;) {
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Annulé");
                        total+=n;if(total>limit)throw new IOException("Limite dépassée");out.write(buffer,0,n);progress.accept(total);
                    }
                    if(expected>=0 && total!=expected)throw new IOException("Réponse tronquée");
                    if(deadline.isDone())throw new IOException("Délai de téléchargement dépassé");
                    return;
                } finally {deadline.cancel(false);timer.shutdownNow();}
            }
        }
        throw new IOException("Trop de redirections");
    }
}
