package io.github.cocosip.dcm4che.imageio.codecs.jpeg2000.common;

public final class Jpeg2000Marker {
    public static final int SOC = 0x4f;
    public static final int CAP = 0x50;
    public static final int SIZ = 0x51;
    public static final int COD = 0x52;
    public static final int COC = 0x53;
    public static final int TLM = 0x55;
    public static final int PLM = 0x57;
    public static final int PLT = 0x58;
    public static final int CPF = 0x59;
    public static final int QCD = 0x5c;
    public static final int QCC = 0x5d;
    public static final int RGN = 0x5e;
    public static final int POC = 0x5f;
    public static final int PPM = 0x60;
    public static final int PPT = 0x61;
    public static final int CRG = 0x63;
    public static final int COM = 0x64;
    public static final int SOT = 0x90;
    public static final int SOP = 0x91;
    public static final int EPH = 0x92;
    public static final int SOD = 0x93;
    public static final int EOC = 0xd9;

    private Jpeg2000Marker() {
    }

    public static boolean hasLength(int marker) {
        return marker != SOC && marker != SOD && marker != EOC && marker != EPH;
    }

    public static boolean isKnown(int marker) {
        switch (marker) {
            case SOC:
            case CAP:
            case SIZ:
            case COD:
            case COC:
            case TLM:
            case PLM:
            case PLT:
            case CPF:
            case QCD:
            case QCC:
            case RGN:
            case POC:
            case PPM:
            case PPT:
            case CRG:
            case COM:
            case SOT:
            case SOP:
            case EPH:
            case SOD:
            case EOC:
                return true;
            default:
                return false;
        }
    }

    public static String name(int marker) {
        switch (marker) {
            case SOC:
                return "SOC";
            case CAP:
                return "CAP";
            case SIZ:
                return "SIZ";
            case COD:
                return "COD";
            case COC:
                return "COC";
            case TLM:
                return "TLM";
            case PLM:
                return "PLM";
            case PLT:
                return "PLT";
            case CPF:
                return "CPF";
            case QCD:
                return "QCD";
            case QCC:
                return "QCC";
            case RGN:
                return "RGN";
            case POC:
                return "POC";
            case PPM:
                return "PPM";
            case PPT:
                return "PPT";
            case CRG:
                return "CRG";
            case COM:
                return "COM";
            case SOT:
                return "SOT";
            case SOP:
                return "SOP";
            case EPH:
                return "EPH";
            case SOD:
                return "SOD";
            case EOC:
                return "EOC";
            default:
                return String.format("0x%02X", marker & 0xff);
        }
    }
}
