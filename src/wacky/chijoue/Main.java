package wacky.chijoue;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

public class Main {

	//DDした画像を読み込む
	public static void main(String[] args) {
		//第一段階、画像の読み込み
	    BufferedImage readImage = null;
	    String fileName = null;
		try{
			File file = new File(args[0]);
		      readImage = ImageIO.read(file);
		      String fileNameExt = file.getName();//拡張子をとっぱらう。
		      int index = fileNameExt.lastIndexOf('.');
		      if(index >=0){
		    	  fileName = fileNameExt.substring(0, index);
		      }else{
		    	  fileName = fileNameExt;
		      }

		}catch(Exception e){
		      e.printStackTrace();
		}

		int width =readImage.getWidth();
		int length =readImage.getHeight();
		System.out.println("Image size : " + width + "x" + length);


		//第二段階、画像のインデックスカラー化
		double r[][] = new double[width][length];
		double g[][] = new double[width][length];
		double b[][] = new double[width][length];
		Color colors[][] = new Color[width][length];

		for(int z=0;z<length;z++){
			for(int x=0;x<width;x++){
				//各点の色を加算
				r[x][z] += readImage.getRGB(x,z) >>16 & 0xff;
				g[x][z] += readImage.getRGB(x,z) >> 8 & 0xff;
				b[x][z] += readImage.getRGB(x,z) & 0xff;

				if(r[x][z] < -45) r[x][z] = -45;
				else if(r[x][z] > 300) r[x][z] = 300;
				if(g[x][z] < -45) g[x][z] = -45;
				else if(g[x][z] > 300) g[x][z] = 300;
				if(b[x][z] < -45) b[x][z] = -45;
				else if(b[x][z] > 300) b[x][z] = 300;

				double old = 1000000;
				double i;

				//最も近いインデックスカラーを探す
				for(Color color: Color.values()){
					i =	Math.abs(r[x][z] - color.getR())*Math.abs(r[x][z] - color.getR()) + Math.abs(g[x][z] - color.getG())*Math.abs(g[x][z] - color.getG()) + Math.abs(b[x][z] - color.getB())*Math.abs(b[x][z] - color.getB());
					if(old > i){
						colors[x][z] = color;
						old = i;
					}
				}
				//誤差
				double errorR = r[x][z] - colors[x][z].getR();
				double errorG = g[x][z] - colors[x][z].getG();
				double errorB = b[x][z] - colors[x][z].getB();

				//拡散
				if(x != width -1){
					r[x+1][z] += errorR*7/16;
					g[x+1][z] += errorG*7/16;
					b[x+1][z] += errorB*7/16;
				}
				if(z != length -1){
					if(x!=0){
						r[x-1][z+1] += errorR*3/16;
						g[x-1][z+1] += errorG*3/16;
						b[x-1][z+1] += errorB*3/16;
					}
					r[x][z+1] += errorR*5/16;
					g[x][z+1] += errorG*5/16;
					b[x][z+1] += errorB*5/16;

					if(x!=width -1){
						r[x+1][z+1] += errorR*1/16;
						g[x+1][z+1] += errorG*1/16;
						b[x+1][z+1] += errorB*1/16;
					}
				}

				readImage.setRGB(x, z, colors[x][z].getR() << 16 | colors[x][z].getG() << 8 | colors[x][z].getB());
			}
		}

		System.out.println("Image load Complete.");
		File f = new File(fileName + "_conv.png");
		try{//透過pngはバグる？
			ImageIO.write(readImage, "png", f);
		}catch(Exception e){
			e.printStackTrace();
		}


		//第三段階、ブロック高度の決定 上端に1ブロック追加する。
		length++;
		int y[][] = new int[width][length];
		int height = 0;//全体
		int yzmin[] = new int[width];//zごとの列
		int yzmax[] = new int[width];

		for(int z=0;z<length-1;z++){
			for(int x=0;x<width;x++){
				//水以外
				y[x][z+1] = y[x][z] + colors[x][z].getHeight();

				//高度補正
				if(y[x][z+1] > 64 && colors[x][z].getHeight() == -1){
					y[x][z+1] = 32;
				}
				else if(y[x][z+1] < -64 && colors[x][z].getHeight() == 1){
					y[x][z+1] = -32;
				}
				//高さ測定
				if(yzmin[x] > y[x][z+1]){
					yzmin[x] = y[x][z+1];
				}else if(yzmax[x] < y[x][z+1]){
					yzmax[x] = y[x][z+1];
				}
			}
		}

		for(int x=0;x<width;x++){
			if(height <= yzmax[x] - yzmin[x]){
				height = yzmax[x] - yzmin[x] + 1;//高さ
			}
		}

		System.out.println("Schematic Width  : " + width);
		System.out.println("Schematic Length : " + length);
		System.out.println("Schematic Height : " + height);

		//第4段階、xzy順にブロック情報を並べる
		byte Blocks[] = new byte[width * (length)* height];
		byte Data[] = new byte[width * (length)* height];

		//上端の石ブロック
		for(int x=0;x<width;x++){
			Blocks[width * (length) * (-yzmin[x]) + x] = 1;
		}

		//それ以外
		for(int z=0;z<length-1;z++){
			for(int x=0;x<width;x++){
				int i = width * (length) * (y[x][z+1] - yzmin[x]) + width * (z+1) + x;
				Blocks[i] = (byte) colors[x][z].getBlock();
				Data[i] = (byte) colors[x][z].getData();
			}
		}


		//最終、ファイル書き出し
		GZIPOutputStream gz = null;

		try{
			gz = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(fileName +".schematic")));

			gz.write(new byte[]{0x0A,0x00,0x09});//NBTTagCompound,9文字
			gz.write(new String("Schematic").getBytes());

			gz.write(new byte[]{0x02,0x00,0x05});//Short,5文字
			gz.write(new String("Width").getBytes());
			gz.write(ByteBuffer.allocate(2).putShort((short)width).array());
			gz.write(new byte[]{0x02,0x00,0x06});//Short,6文字
			gz.write(new String("Length").getBytes());
			gz.write(ByteBuffer.allocate(2).putShort((short)length).array());
			gz.write(new byte[]{0x02,0x00,0x06});//Short,6文字
			gz.write(new String("Height").getBytes());
			gz.write(ByteBuffer.allocate(2).putShort((short)height).array());

			gz.write(new byte[]{0x08,0x00,0x09});//String,9文字
			gz.write(new String("Materials").getBytes());
			gz.write(new byte[]{0x00,0x05});//5文字
			gz.write(new String("Alpha").getBytes());
			gz.write(new byte[]{0x09,0x00,0x08});//List,8文字
			gz.write(new String("Entities").getBytes());
			gz.write(new byte[]{0x00,0x00,0x00,0x00,0x00});//byteとint 0-0000
			gz.write(new byte[]{0x09,0x00,0x0c});//List,12文字
			gz.write(new String("TileEntities").getBytes());
			gz.write(new byte[]{0x00,0x00,0x00,0x00,0x00});//byteとint 0-0000

			gz.write(new byte[]{0x07,0x00,0x06});//Byte配列,6文字
			gz.write(new String("Blocks").getBytes());
			gz.write(ByteBuffer.allocate(4).putInt(Blocks.length).array());//Byte配列の長さ
			gz.write(Blocks);

			gz.write(new byte[]{0x07,0x00,0x04});//Byte配列,4文字
			gz.write(new String("Data").getBytes());
			gz.write(ByteBuffer.allocate(4).putInt(Data.length).array());//Byte配列の長さ
			gz.write(Data);
			gz.write(0x00);//End

			gz.finish();

		}catch(Exception e){
			e.printStackTrace();
		}finally {
			try {
				gz.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("Schematic saved Succeesfully.");

	}

}
