package cn.icheny.download;

/**
 * ----------------------
 * 代码千万行
 * 注释第一行
 * 代码不注释
 * 改bug两行泪
 * -----------------------
 *
 * @author weiyitai
 * @date 2019/9/9 0009  14:32
 */
public class Util {

    public static long parseLong(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }

}
