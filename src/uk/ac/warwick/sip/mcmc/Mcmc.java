package uk.ac.warwick.sip.mcmc;

import org.apache.commons.math3.random.MersenneTwister;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;

/**CLASS: MCMC
 * Framework for MCMC, method to be implemented:
 *   -step() (one step)
 *   -run() (multiple steps to the end of the chain)
 * The target distribution is provided via the constructor.
 * The chain length and a MersenneTwister is to be provided via the constructor.
 * The samples are stored in the member variable chainArray as a design matrix format
 */
public abstract class Mcmc {
  
  protected TargetDistribution target; //target distribution for metropolis hastings
  //matrix containing the value of the chain at each step
  //matrix is of size chainLength X nDim, EJML is row major
  //initially it will contain random Gaussian
  //the intial value is at the origin, this can be set using the method setInitialStep
  protected SimpleMatrix chainArray;
  protected SimpleMatrix chainMean; //the mean of the chain at the current step (column vector)
  protected SimpleMatrix chainCovariance; //covariance of the chain at the current step
  //array of acceptance rate at each step
  protected double [] acceptanceArray;
  
  protected int nStep = 0; //number of steps taken so far
  protected int chainLength; //the total length of the chain
  protected int nAccept = 0; //the number of acceptance steps taken
  
  protected MersenneTwister rng; //random number generator
  
  //temporary member variables, statistics based on the chain and burn in
  //these member variables will be instantised when the method calculateChainStatistics is called
  
  //the mean of the chain (for each dimension) after burn in
  protected SimpleMatrix posteriorExpectation;
  //monte carlo error of the mean, after burn in
  protected SimpleMatrix monteCarloError;
  //covariance of the chain, after burn in
  protected SimpleMatrix posteriorCovariance;
  
  /**CONSTRUCTOR
   * @param target Object which has a method to call the pdf
   * @param chainLength Length of the chain to be obtained
   * @param rng Random number generator all the random numbers
   */
  public Mcmc(TargetDistribution target, int chainLength,
      MersenneTwister rng) {
    this.target = target;
    this.chainLength = chainLength;
    this.chainArray = new SimpleMatrix(this.chainLength, this.getNDim());
    this.chainMean = new SimpleMatrix(this.getNDim(), 1);
    this.chainCovariance = new SimpleMatrix(this.getNDim(), this.getNDim());
    this.acceptanceArray = new double [this.chainLength-1];
    this.rng = rng;
  }
  
  /**CONSTRUCTOR
   * Constructor for extending the length of the chain and resume running it
   * Does a shallow copy of the provided chain and extending the member variable chainArray
   * @param chain Chain to be extended
   * @param nMoreSteps Number of steps to be extended
   */
  public Mcmc(Mcmc chain, int nMoreSteps){
    //do a shallow copy of chain
    this.target = chain.target;
    this.chainLength = chain.chainLength + nMoreSteps;
    this.chainArray = new SimpleMatrix(this.chainLength + nMoreSteps, this.getNDim());
    this.chainMean = chain.chainMean;
    this.chainCovariance = chain.chainCovariance;
    this.acceptanceArray = chain.acceptanceArray;
    this.nStep = chain.nStep;
    this.nAccept = chain.nAccept;
    this.rng = chain.rng;
    
    //deep copy the content of chainArray the old chain to the new chain
    //these contain the values of the MCMC
    for (int i=0; i<chain.chainArray.getNumElements(); i++) {
      this.chainArray.set(i, chain.chainArray.get(i));
    }
    
  }
  
  /**METHOD: STEP
   * Does a MCMC step
   */
  public abstract void step();
  
  /**METHOD: RUN
   * Does (chainLength - 1) MCMC steps
   */
  public abstract void run();
  
  /**METHOD: ACCEPT STEP
   * The chainArray is updated at this.nStep+1.
   * With probability acceptProb, the chain takes the value proposal, otherwise current
   * The random uniform numbers in random01Array are used
   * @param acceptProb Acceptance probability
   * @param current Column vector containing the value of the chain now
   * @param proposal Column vector of the proposal step
   */
  protected void acceptStep(double acceptProb, SimpleMatrix current, SimpleMatrix proposal) {
    //get a random number between 0 and 1
    //with acceptProb chance, accept the sample
    SimpleMatrix acceptedStep = current;
    if (this.rng.nextDouble() < acceptProb){
      acceptedStep = proposal;
      //increment the number of acceptance steps
      this.nAccept++;
    }
    //save x to the chain array
    for (int iDim = 0; iDim<this.getNDim(); iDim++) {
      this.chainArray.set(this.nStep+1, iDim, acceptedStep.get(iDim));
    }
  }
  
  /**METHOD: SET INITIAL VALUE
   * Set the initial value of the chain, to be called before running the chain
   * @param initialValue double [] containing the values of the initial position
   */
  public void setInitialValue(double [] initialValue) {
    for (int i=0; i<initialValue.length; i++) {
      this.chainArray.set(i, initialValue[i]);
    }
  }
    
  /**METHOD: UPDATE STATISTICS
   * Update the acceptanceArray, nStep, chainMean and chainCovariance
   */
  protected void updateStatistics(){
    
    //work out the acceptance rate at this stage
    //acceptance rate = (number of acceptance steps) / (number of steps)
    //the acceptance rate keeps track of the acceptance rate from and including the 1st step
    //(not from the initial value)
    //note that this.nStep has been incremented when metropolisHastingsStep was called
    this.acceptanceArray[this.nStep] = ((double)(this.nAccept)) / ((double)(this.nStep+1));
    
    //increment the number of steps taken
    this.nStep++;
    //n is the chain length, so it is the number of steps + 1 (from the initial value)
    double n = (double) (this.nStep+1);
    
    //instantiate vector of the current value of the chain
    SimpleMatrix x = this.chainArray.extractVector(true, this.nStep);
    //transpose it
    CommonOps_DDRM.transpose(x.getDDRM());
    
    //update the mean using the previous mean
    CommonOps_DDRM.scale(n-1, this.chainMean.getDDRM());
    CommonOps_DDRM.addEquals(this.chainMean.getDDRM(), x.getDDRM());
    CommonOps_DDRM.divide(this.chainMean.getDDRM(), n);
    
    //if this is the first step
    if (this.nStep == 1){
      //get the initial value of the chain and transpose it to be a column vector
      SimpleMatrix x1 = this.chainArray.extractVector(true, 0);
      CommonOps_DDRM.transpose(x1.getDDRM());
      //calculate the difference between the initial and the mean
      SimpleMatrix r1 = x1.minus(this.chainMean);
      //calculate the difference between the most recent value and the mean
      SimpleMatrix r2 = x.minus(this.chainMean);
      //calculate the covariance from scratch
      CommonOps_DDRM.multOuter(r1.getDDRM(), this.chainCovariance.getDDRM());
      DMatrixRMaj r2Outer = new DMatrixRMaj(this.getNDim(), this.getNDim());
      CommonOps_DDRM.multOuter(r2.getDDRM(), r2Outer);
      CommonOps_DDRM.addEquals(this.chainCovariance.getDDRM(), r2Outer);
      
    }
    
    //else update the covariance recursively
    else{
      //calculate the difference between the most recent value and the mean
      SimpleMatrix r2 = x.minus(this.chainMean);
      //update the covariance using the previous covariance
      CommonOps_DDRM.scale(n-2, this.chainCovariance.getDDRM());
      DMatrixRMaj r2Outer = new DMatrixRMaj(this.getNDim(), this.getNDim());
      CommonOps_DDRM.multOuter(r2.getDDRM(), r2Outer);
      CommonOps_DDRM.scale(n/(n-1), r2Outer);
      CommonOps_DDRM.addEquals(this.chainCovariance.getDDRM(), r2Outer);
      CommonOps_DDRM.divide(this.chainCovariance.getDDRM(), n-1);
    }
    
  }
  
  /**METHOD: GET AUTOCORRELATION FUNCTION
   * Calculates the sample autocorrelation function for lags 0 to nLag
   * Results are returned in a double []
   * @param nLag The maximum lag to be obtained
   * @return The acf at lag 0, 1, 2, ..., nLag
   */
  public double [] getAcf(int nDim, int nLag) {
    
    //declare array for the acf, for lag 0,1,2,...,nLag
    double [] acf = new double[nLag+1];
    //retrieve the chain
    SimpleMatrix chain = this.chainArray.extractVector(false, nDim);
    
    //work out the sample mean and centre the chain at the sample mean
    double mean = chain.elementSum() / ((double) chain.getNumElements());
    chain = chain.minus(mean);
    //work out the S_x_xLag for all lags, see method getSxxlag
    for (int i=0; i<=nLag; i++) {
      acf[i] = this.getSxxlag(chain, i);
    }
    //normalise the acf
    for (int i=1; i<=nLag; i++) {
      acf[i] /= acf[0];
    }
    acf[0] = 1.0;
    
    return acf;
    
  }
  
  /**METHOD: GET S_X_XLAG
   * Calculates \sum_{i=0}^{n-k} (x_i)(x_{i+k}) where k is the lag
   * For k = 0, this is the sum of squares
   * @param chain Column vector containing the chain, centred around the mean
   * @param lag k
   * @return Sum of lagged elements
   */
  protected double getSxxlag(SimpleMatrix chain, int lag) {
    //trim the front of the chain
    SimpleMatrix chainFrontTrim = chain.extractMatrix(lag, chain.numRows(), 0, 1);
    //trim the back of the chain
    SimpleMatrix chainEndTrim = chain.extractMatrix(0, chain.numRows()-lag, 0, 1);
    //multiply the trimmed chains
    return chainFrontTrim.elementMult(chainEndTrim).elementSum();
    
  }
  
  /**METHOD: CALCULATE POSTERIOR STATISTICS
   * Calculates the posterior expectation, posterior covariance and the monte carlo error for the
   * posterior expectation. These then can be obtained using the method getPosteriorExpectation,
   * getPosteriorCovariance and getMonteCarloError
   * @param nBurnIn Number of samples to be ignored at the start of the chain
   */
  public void calculatePosteriorStatistics(int nBurnIn) {
    
    //instantiate matrices for the posterior statistics
    this.posteriorExpectation = new SimpleMatrix(this.getNDim(),1);
    this.monteCarloError = new SimpleMatrix(this.getNDim(),1);
    this.posteriorCovariance = new SimpleMatrix(this.getNDim(),this.getNDim());
    
    //calculate the posterior statistics
    this.calculatePosteriorExpectation(nBurnIn);
    this.calculateMonteCarloError(nBurnIn);
    this.calculatePosteriorCovariance(nBurnIn);
  }
  
  /**METHOD: CALCULATE POSTERIOR EXPECTATION
   * Calulates the posterior expectation, with regards to the burn in
   * @param nBurnIn Number of samples to be ignored at the start of the chain
   */
  protected void calculatePosteriorExpectation(int nBurnIn) {
    //for each dimension, calculate the sample mean
    for (int i=0; i<this.getNDim(); i++) {
      //extract the vector from the burn in for this dimension
      SimpleMatrix burntChain = this.chainArray.extractMatrix(nBurnIn, SimpleMatrix.END, i, i+1);
      //calculate sample mean
      this.posteriorExpectation.set(i,
          burntChain.elementSum() / ( (double) (this.chainLength - nBurnIn)) );
    }
  }
  
  /**METHOD: CALCULATE MONTE CARLO ERROR
   * Calculate the Monte Carlo error in calculate the mean, this is done using batching
   * The number of batches used is sqrt(n)
   * @param nBurnIn Number of samples to be ignored at the start of the chain
   */
  protected void calculateMonteCarloError(int nBurnIn) {
    //for each dimension
    for (int i=0; i<this.getNDim(); i++) {
      //extract the vector from the burn in for this dimension
      SimpleMatrix burntChain = this.chainArray.extractMatrix(nBurnIn, SimpleMatrix.END, i, i+1);
      int n = burntChain.numRows(); //get the number of samples of the burnt chain
      
      //calculate the number of batches
      int nBatch = (int) Math.round(Math.sqrt((double) n));
      //declare matrices to store the following
      SimpleMatrix batchLength = new SimpleMatrix(nBatch,1); //the length of each batch
      SimpleMatrix batchArray = new SimpleMatrix(nBatch,1); //the mean of each batch
      
      //declare variables for pointing to specific parts of the chain in order to obtain the batch
      //samples
      int indexStart = 0;
      int indexEnd;
      //get double versions of nBatch and n
      double nBatchDouble = (double) nBatch;
      double chainLengthDouble = (double) n;
      
      //for each batch
      for (int iBatch=0; iBatch<nBatch; iBatch++) {
        //get the pointer of the end of the batch + 1
        indexEnd = (int) Math.round(((double)(iBatch+1)) * chainLengthDouble / nBatchDouble);
        //save the length of this batch
        batchLength.set(iBatch, (double) (indexEnd - indexStart));
        //get the samples from this batch
        SimpleMatrix batchVector = burntChain.extractMatrix(indexStart, indexEnd, 0, 1);
        //work out the sample mean and save it
        batchArray.set(iBatch, batchVector.elementSum()/ batchLength.get(iBatch) );
        //set the pointer for the next batch
        indexStart = indexEnd;
      }
      
      //calculate the monte carlo error and save it
      double monteCarloError_i = batchArray.minus(this.posteriorExpectation.get(i)).elementPower(2)
          .elementMult(batchLength).elementSum();
      monteCarloError_i /= ((double)(nBatch * n));
      monteCarloError_i = Math.sqrt(monteCarloError_i);
      this.monteCarloError.set(i, monteCarloError_i);
    }
  }
  
  /**METHOD: CALCULATE POSTERIOR COVARIANCE
   * Calculate the posterior covariance, with regards to the burn in
   * @param nBurnIn Number of samples to be ignored at the start of the chain
   */
  protected void calculatePosteriorCovariance(int nBurnIn) {
    //for each sample
    for (int i=0; i<(this.chainLength-nBurnIn); i++) {
      //get the vector for this step
      SimpleMatrix x = this.chainArray.extractVector(true, nBurnIn+i); //this is a row vector
      CommonOps_DDRM.transpose(x.getDDRM()); //transpose for a column vector
      //x subtract mean
      CommonOps_DDRM.subtractEquals(x.getDDRM(), this.posteriorExpectation.getDDRM());
      //instantiate a matrix for this outer product
      SimpleMatrix xOuter = new SimpleMatrix(this.getNDim(), this.getNDim());
      CommonOps_DDRM.multOuter(x.getDDRM(), xOuter.getDDRM());
      //+= the outer product to this.posteriorCovariance
      CommonOps_DDRM.addEquals(this.posteriorCovariance.getDDRM(), xOuter.getDDRM());
    }
    //use the bias corrected divide
    CommonOps_DDRM.divide(this.posteriorCovariance.getDDRM()
        , (double) (this.chainLength-nBurnIn-1) );
    
  }
  
  /**METHOD: GET DIFFERENCE LN ERROR
   * Call the calculatePosteriorStatistics prior to calling this method
   * Calculate ln(chain std) - ln (monte carlo error)
   * This gives some indiciation how large/small the monte carlo error, the chain should stop if
   * this difference is small, e.g. <6.9
   * @return Array of ln(chain std) - ln (monte carlo error), an entry for each dimension
   */
  public double [] getDifferenceLnError() {
    SimpleMatrix posteriorStd = this.posteriorCovariance.diag().elementPower(0.5);
    return posteriorStd.elementLog().minus(this.monteCarloError.elementLog()).getDDRM().getData();
  }
  
  /**METHOD: GET N DIM
   * @return The number of dimensions the target distribution has
   */
  public int getNDim() {
    return this.target.getNDim();
  }
  
  /**METHOD: GET ACCEPTANCE RATE
   * @return The estimate acceptance rate at each step
   */
  public double[] getAcceptanceRate() {
    return this.acceptanceArray;
  }
  
  /**METHOD: GET CHAIN
   * @return double array of the chain, row major
   */
  public double [] getChain() {
    return this.chainArray.getDDRM().getData();
  }
  
  /**METHOD: GET CHAIN (of a specific dimension)
   * @param nDim Which dimension to extract from the chain
   * @return double array of the chain, each element correspond to a MCMC step
   */
  public double [] getChain(int nDim) {
    return this.chainArray.extractVector(false, nDim).getDDRM().getData();
  }
  
  /**METHOD: GET END OF CHAIN
   * @return double array, vector of the last postion of the chain
   */
  public double [] getEndOfChain() {
    return this.chainArray.extractVector(true, this.nStep).getDDRM().getData();
  }
  
  /**METHOD: GET CHAIN MEAN
   * @return The chain mean at the current step
   */
  public double[] getChainMean() {
    return this.chainMean.getDDRM().getData();
  }
  
  /**METHOD: GET CHAIN COVARIANCE
   * @return The chain covariance at the current step (nDim x nDim symmetrical matrix)
   */
  public double [] getChainCovariance() {
    return this.chainCovariance.getDDRM().getData();
  }
  
  /**METHOD: GET POSTERIOR EXPECTATAION
   * Call the calculatePosteriorStatistics prior to calling this method
   * @return Posterior expectation with regards to burning in (nDim vector)
   */
  public double [] getPosteriorExpectation() {
    return this.posteriorExpectation.getDDRM().getData();
  }
  
  /**METHOD: GET POSTERIOR EXPECTATAION
   * Call the calculatePosteriorStatistics prior to calling this method
   * @return Monte carlo error of the posterior expectataion (nDim vector)
   */
  public double [] getMonteCarloError() {
    return this.monteCarloError.getDDRM().getData();
  }
  
  /**METHOD: GET POSTERIOR COVARIANCE
   * Call the calculatePosteriorStatistics prior to calling this method
   * @return Posterior covariance with regards to burning in (nDim x nDim symmetrical matrix)
   */
  public double [] getPosteriorCovariance() {
    return this.posteriorCovariance.getDDRM().getData();
  }
  
}
