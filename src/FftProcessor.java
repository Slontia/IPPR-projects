import java.awt.image.BufferedImage;

class FFTProcessor extends SignalTransformation {
	final long RANGE_EDGE = 255;
	final long POWER_EDGE = 255;
	
	public FFTProcessor(BufferedImage image) {
		super(image);
	}
	
	public FFTProcessor(int[][] greyMatrix) {
		super(greyMatrix);
	}
	
	private int roundPower(int x) {
		if (((x - 1) & x) == 0) {
			return x;
		}
		int res = 0;
		for (res = 1; x > 0; x >>= 1, res <<= 1);
		return res;
	}
	
	// inverseFlag = 1 to FFT
	// inverseFlag = -1 to IFFT
	private FourierComplex[] fft1d(FourierComplex[] sigs, boolean inverseFlag) {
		// round signals
		int len = sigs.length;
		int N = roundPower(len);
		FourierComplex[] newSigs;
		try {
			newSigs = new FourierComplex[N];
		} catch (Exception e) {
			return null;
		}
		for (int i = 0; i < N; i++) {
			newSigs[i] = sigs[i % len];
		}
		
		// cal WN
		FourierComplex[] WN = new FourierComplex[N / 2];
		for (int i = 0; i < N / 2; i++) {
			WN[i] = new FourierComplex(Math.cos(2 * Math.PI * i / N), (inverseFlag ? 1 : -1) * Math.sin(2 * Math.PI * i / N));
		}
		
		// initial list
		int[] indexMap = new int[N];
		indexMap[0] = 0;
		for (int i = 1; i < N; i *= 2) {
			int step = N / 2 / i;
			for (int j = 0; j < N; j += step * 2) {
				indexMap[j + step] = indexMap[j] + i;
			}
		}
		FourierComplex[] res = new FourierComplex[N];
		for (int i = 0; i < N; i++) {
			res[i] = newSigs[indexMap[i]];
		}
		
		// dp
		for (int i = 1; i < N; i *= 2) { // level
			for (int j = 0; j < N / 2 / i; j++) { // group
				for (int k = 0; k < i; k++) { // pair
					int index1 = j * i * 2 + k;
					int index2 = index1 + i;
					FourierComplex temp1 = res[index1];
					FourierComplex temp2 = WN[N / 2 / i * k].mul(res[index2]);
					res[index1] = temp1.add(temp2);
					res[index2] = temp1.sub(temp2);
				}
			}
		}
		
		if (inverseFlag) {
			for (int i = 0; i < N; i++) {
				res[i] = res[i].div(N);
			}
		}
		
		return res;
	}
	
	private FourierComplex[][] fft2d(FourierComplex[][] sigs, boolean inverseFlag) {
		int height = sigs.length;
		if (height == 0) {
			return null;
		}
		int width = sigs[0].length;
		int N = roundPower(height);
		int M = roundPower(width);
		FourierComplex[][] res;
		try {
			res = new FourierComplex[N][M];
		} catch (Exception e) {
			return null;
		}
		
		for (int i = 0; i < height; i++) {
			res[i] = fft1d(sigs[i], inverseFlag);
		}
		
		for (int j = 0; j < M; j++) {
			FourierComplex[] col = new FourierComplex[height];
			for (int i = 0; i < height; i++) {
				col[i] = res[i][j];
			}
			FourierComplex[] newCol = fft1d(col, inverseFlag);
			for (int i = 0; i < N; i++) {
				res[i][j] = newCol[i];
			}
		}
		
		return res;
	}
	
	// move range image to center
	private int[][] translation(int[][] matrix, int N, int M) {
		int i, j;
		int[][] res = new int[N][M];
		for (i = 0; i < N / 2; i++) {
			for (j = 0; j < M / 2; j++) {
				res[i][j] = matrix[i + N / 2][j + M / 2];
				res[i + N / 2][j + M / 2] = matrix[i][j];
			}
			for (; j < M; j++) {
				res[i][j] = matrix[i + N / 2][j - M / 2];
				res[i + N / 2][j - M / 2] = matrix[i][j];
			}
		}
		return res;
	}
	
	/** API **/
	public FourierComplex[][] fourierTransformation() {
		if (height == 0 || width == 0) {
			return null;
		}
		FourierComplex[][] sigs = new FourierComplex[width][height];
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				sigs[i][j] = new FourierComplex(greyMatrix[i][j]);
			}
		}
		FourierComplex[][] res = fft2d(sigs, false);
		return res;
	}
	
	public FourierComplex[][] fourierTransformation(String outputlabel) {
		FourierComplex[][] res = fourierTransformation();
		outputResult(outputlabel, res);
		return res;
	}
	
	/** API **/
	public BufferedImage fourierInverse(String outputLabel, FourierComplex[][] sigs) {
		FourierComplex[][] inverse = fft2d(sigs, true);
		long[][] greyMatrix = new long[width][height];
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				greyMatrix[i][j] = (long) inverse[i][j].getReal();
			}
		}
		BufferedImage image = getGreyImage(normalizeImage(greyMatrix, GREY_SCALE_RANGE - 1));
		outputImage(outputLabel, "png", image);
		outputResult(outputLabel, sigs);
		return image;
	}
	
	private void outputResult(String outputLabel, FourierComplex[][] frequencyMatrix) {
		if (frequencyMatrix == null) {
			return;
		}
		int N = frequencyMatrix.length;
		int M = frequencyMatrix[0].length;
		long[][] powerMatrix = new long[N][M];
		long[][] rangeMatrix = new long[N][M];
		long[][] phaseMatrix = new long[N][M];
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < M; j++) {
				FourierComplex fComplex = frequencyMatrix[i][j];
				powerMatrix[i][j] = (long) fComplex.getPower();
				rangeMatrix[i][j] = (long) fComplex.getRange();
				phaseMatrix[i][j] = (long) fComplex.getPhase();
			}
		}
		int[][] powerGreyMatrix = translation(normalizeImage(powerMatrix, POWER_EDGE), N, M);
		int[][] rangeGreyMatrix = translation(normalizeImage(rangeMatrix, RANGE_EDGE), N, M);
		int[][] phaseGreyMatrix = translation(normalizeMatrix(phaseMatrix), N, M);
		outputImage(outputLabel + "(power)", "png", getGreyImage(powerGreyMatrix));
		outputImage(outputLabel + "(range)", "png", getGreyImage(rangeGreyMatrix));
		outputImage(outputLabel + "(phase)", "png", getGreyImage(phaseGreyMatrix));
	}
}