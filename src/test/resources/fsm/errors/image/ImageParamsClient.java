package fsm.errors.image;

public class ImageParamsClient {

    public static void typeWithoutExplicitMode() {
        ImageParams params = new ImageParams();
        params.setTilingMode(2);
        params.setCompressionType("JPEG"); // state error: found startCompression, expected compressionExplicit
    }

    public static void writeWithoutTiling() {
        ImageParams params = new ImageParams(2);
        params.setCompressionMode(2);
        params.setCompressionType("JPEG");
        params.write(); // state error: found tilingExplicit, expected tilingSet and compressionSet
    }
}
