import javax.imageio.ImageIO;

/** Checks the packaged ImageIO service entries without using test classes. */
public final class Htj2kSpiSmoke {
    public static void main(String[] args) {
        for (String name : new String[] {
                "htj2k-lossless", "htj2k-lossless-rpcl", "htj2k-lossy"}) {
            if (!ImageIO.getImageReadersByFormatName(name).hasNext()
                    || !ImageIO.getImageWritersByFormatName(name).hasNext()) {
                throw new IllegalStateException("Missing HTJ2K SPI pair: " + name);
            }
            System.out.println(name + ": reader and writer found");
        }
    }
}
