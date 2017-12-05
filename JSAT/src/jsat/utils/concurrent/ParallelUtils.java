package jsat.utils.concurrent;
import static java.lang.Math.min;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jsat.utils.FakeExecutor;
import jsat.utils.ListUtils;
import jsat.utils.SystemInfo;

/**
 *
 * @author Edward Raff
 */
public class ParallelUtils
{
    /**
     * This helper method provides a convenient way to break up a computation
     * across <tt>N</tt> items into contiguous ranges that can be processed
     * independently in parallel.
     *
     * @param parallel a boolean indicating if the work should be done in
     * parallel. If false, it will run single-threaded. This is for code
     * convenience so that only one set of code is needed to handle both cases.
     * @param N the total number of items to process
     * @param lcr the runnable over a contiguous range 
     */
    public static void run(boolean parallel, int N, LoopChunkRunner lcr)
    {
        ExecutorService threadPool = Executors.newFixedThreadPool(SystemInfo.LogicalCores);
        run(parallel, N, lcr, threadPool);
        threadPool.shutdownNow();
    }
    
    /**
     * This helper method provides a convenient way to break up a computation
     * across <tt>N</tt> items into contiguous ranges that can be processed
     * independently in parallel.
     *
     * @param parallel a boolean indicating if the work should be done in
     * parallel. If false, it will run single-threaded. This is for code
     * convenience so that only one set of code is needed to handle both cases.
     * @param N the total number of items to process. 
     * @param lcr the runnable over a contiguous range 
     * @param threadPool the source of threads for the computation 
     */
    public static void run(boolean parallel, int N, LoopChunkRunner lcr, ExecutorService threadPool)
    {
        if(!parallel)
        {
            lcr.run(0, N);
            return;
        }
        
        final CountDownLatch latch = new CountDownLatch(SystemInfo.LogicalCores);

        IntStream.range(0, SystemInfo.LogicalCores).forEach(threadID ->
        {
            threadPool.submit(() ->
            {
                int start = ParallelUtils.getStartBlock(N, threadID);
                int end = ParallelUtils.getEndBlock(N, threadID);
                lcr.run(start, end);
                latch.countDown();
            });
        });

        try
        {
            latch.await();
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(ParallelUtils.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    public static ExecutorService getNewExecutor(boolean parallel)
    {
        if(parallel)
            return Executors.newFixedThreadPool(SystemInfo.LogicalCores);
        else
            return new FakeExecutor();
    }
    
    
    public static <T> Stream<T> streamP(Stream<T> source, boolean parallel)
    {
        if(parallel)
            return source.parallel();
        else
            return source;
    }
    
    public static IntStream range(int end,  boolean parallel)
    {
        return range(0, end, parallel);
    }
    
    public static IntStream range(int start, int end,  boolean parallel)
    {
        if(parallel)
        {
            /*
             * Why do this weirndes instead of call IntStream directly? 
             * IntStream has a habit of not returning a stream that actually 
             * executes in parallel when the range is small. That would make 
             * sense for most cases, but we probably are doing course 
             * parallelism into chunks. So this approach gurantees we get 
             * something that will actually run in parallel. 
             */
            return ListUtils.range(start, end).stream().parallel().mapToInt(i -> i);
        }
        else
            return IntStream.range(start, end);
    }
    
    /**
     * Gets the starting index (inclusive) for splitting up a list of items into
     * {@code P} evenly sized blocks. In the event that {@code N} is not evenly 
     * divisible by {@code P}, the size of ranges will differ by at most 1. 
     * @param N the number of items to split up
     * @param ID the block number in [0, {@code P})
     * @param P the number of blocks to break up the items into
     * @return the starting index (inclusive) of the blocks owned by the 
     * {@code ID}'th process. 
     */
    public static int getStartBlock(int N, int ID, int P)
    {
        int rem = N%P;
        int start = (N/P)*ID+min(rem, ID);
        return start;
    }
    
    /**
     * Gets the starting index (inclusive) for splitting up a list of items into
     * {@link SystemInfo#LogicalCores} evenly sized blocks. In the event that
     * {@code N} is not evenly divisible by {@link SystemInfo#LogicalCores}, the
     * size of ranges will differ by at most 1.
     *
     * @param N the number of items to split up
     * @param ID the block number in [0, {@link SystemInfo#LogicalCores})
     * @return the starting index (inclusive) of the blocks owned by the
     * {@code ID}'th process.
     */
    public static int getStartBlock(int N, int ID)
    {
        return getStartBlock(N, ID, SystemInfo.LogicalCores);
    }
    
    /**
     * Gets the ending index (exclusive) for splitting up a list of items into
     * {@code P} evenly sized blocks. In the event that {@code N} is not evenly 
     * divisible by {@code P}, the size of ranges will differ by at most 1. 
     * @param N the number of items to split up
     * @param ID the block number in [0, {@code P})
     * @param P the number of blocks to break up the items into
     * @return the ending index (exclusive) of the blocks owned by the 
     * {@code ID}'th process. 
     */
    public static int getEndBlock(int N, int ID, int P)
    {
        int rem = N%P;
        int start = (N/P)*(ID+1)+min(rem, ID+1);
        return start;
    }

    /**
     * Gets the ending index (exclusive) for splitting up a list of items into
     * {@link SystemInfo#LogicalCores} evenly sized blocks. In the event that
     * {@link SystemInfo#LogicalCores} is not evenly divisible by
     * {@link SystemInfo#LogicalCores}, the size of ranges will differ by at
     * most 1.
     *
     * @param N the number of items to split up
     * @param ID the block number in [0, {@link SystemInfo#LogicalCores})
     * @return the ending index (exclusive) of the blocks owned by the
     * {@code ID}'th process.
     */
    public static int getEndBlock(int N, int ID)
    {
        return getEndBlock(N, ID, SystemInfo.LogicalCores);
    }
}
