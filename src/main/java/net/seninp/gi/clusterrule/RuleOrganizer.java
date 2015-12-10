package net.seninp.gi.clusterrule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import com.apporiented.algorithm.clustering.AverageLinkageStrategy;
import com.apporiented.algorithm.clustering.Cluster;
import com.apporiented.algorithm.clustering.ClusteringAlgorithm;
import com.apporiented.algorithm.clustering.DefaultClusteringAlgorithm;

import net.seninp.gi.logic.GrammarRules;
import net.seninp.gi.logic.RuleInterval;
import net.seninp.gi.logic.SAXMotif;
import net.seninp.gi.logic.SAXPointsNumber;
import net.seninp.gi.logic.SameLengthMotifs;

public class RuleOrganizer {

	/**
	 * Classify the motifs based on their length.
	 * 
	 * It calls "getAllMotifs()" to get all the sub-sequences that were
	 * generated by Sequitur rules in ascending order. Then bins all the
	 * sub-sequences by length based on the length of the first sub-sequence in
	 * each class, that is, the shortest sub-sequence in each class.
	 * 
	 * @param lengthThreshold
	 *            the motif length threshold.
	 */
	public ArrayList<SameLengthMotifs> classifyMotifs(
			double lengthThreshold, GrammarRules grammarRules) {

		// reset vars
		ArrayList<SameLengthMotifs> allClassifiedMotifs = new ArrayList<SameLengthMotifs>();

		// down to business
		ArrayList<SAXMotif> allMotifs = getAllMotifs(grammarRules);

		// is this one better?
		int currentIndex = 0;
		for (SAXMotif tmpMotif : allMotifs) {

			currentIndex++;

			if (tmpMotif.isClassified()) {
				// this breaks the loop flow, so it goes to //for (SAXMotif
				// tempMotif : allMotifs) {
				continue;
			}

			SameLengthMotifs tmpSameLengthMotifs = new SameLengthMotifs();
			int tmpMotifLen = tmpMotif.getPos().getEnd()
					- tmpMotif.getPos().getStart() + 1;
			int minLen = tmpMotifLen;
			int maxLen = tmpMotifLen;

			// TODO: assuming that this motif has not been processed, right?
			ArrayList<SAXMotif> newMotifClass = new ArrayList<SAXMotif>();
			newMotifClass.add(tmpMotif);
			tmpMotif.setClassified(true);

			// TODO: this motif assumed to be the first one of it's class,
			// traverse the rest down
			for (int i = currentIndex; i < allMotifs.size(); i++) {

				SAXMotif anotherMotif = allMotifs.get(i);

				// if the two motifs are similar or not.
				int anotherMotifLen = anotherMotif.getPos().getEnd()
						- anotherMotif.getPos().getStart() + 1;

				// if they have the similar length.
				if (Math.abs(anotherMotifLen - tmpMotifLen) < (tmpMotifLen * lengthThreshold)) {
					newMotifClass.add(anotherMotif);
					anotherMotif.setClassified(true);
					if (anotherMotifLen > maxLen) {
						maxLen = anotherMotifLen;
					} else if (anotherMotifLen < minLen) {
						minLen = anotherMotifLen;
					}
				}
			}

			tmpSameLengthMotifs.setSameLenMotifs(newMotifClass);
			tmpSameLengthMotifs.setMinMotifLen(minLen);
			tmpSameLengthMotifs.setMaxMotifLen(maxLen);
			allClassifiedMotifs.add(tmpSameLengthMotifs);
		}
		return allClassifiedMotifs;
		// System.out.println();
	}

	/**
	 * Stores all the sub-sequences that generated by Sequitur rules into an
	 * array list sorted by sub-sequence length in ascending order.
	 * 
	 * @return the list of all sub-sequences sorted by length in ascending
	 *         order.
	 */
	protected ArrayList<SAXMotif> getAllMotifs(GrammarRules grammarRules) {

		// result
		ArrayList<SAXMotif> allMotifs = new ArrayList<SAXMotif>();

		int ruleNumber = grammarRules.size();

		// iterate over all rules
		for (int i = 0; i < ruleNumber; i++) {

			// iterate over all segments/motifs/sub-sequences which correspond
			// to the rule
			ArrayList<RuleInterval> arrPos = grammarRules.getRuleRecord(i)
					.getRuleIntervals();
			for (RuleInterval saxPos : arrPos) {
				SAXMotif motif = new SAXMotif();
				motif.setPos(saxPos);
				motif.setRuleIndex(i);
				motif.setClassified(false);
				allMotifs.add(motif);
			}

		}

		// ascending order
		Collections.sort(allMotifs);
		return allMotifs;
	}

	protected ArrayList<SameLengthMotifs> removeOverlappingInSimiliar(
			ArrayList<SameLengthMotifs> allClassifiedMotifs,
			GrammarRules grammarRules, double[] ts, double thresouldCom) {

		ArrayList<SAXMotif> motifsBeDeleted = new ArrayList<SAXMotif>();

		SAXPointsNumber[] pointsNumberRemoveStrategy = countPointNumber(
				grammarRules, ts);
		for (SameLengthMotifs sameLenMotifs : allClassifiedMotifs) {
			outer: for (int j = 0; j < sameLenMotifs.getSameLenMotifs().size(); j++) {
				SAXMotif tempMotif = sameLenMotifs.getSameLenMotifs().get(j);
				int tempMotifLen = tempMotif.getPos().getEnd()
						- tempMotif.getPos().getStart() + 1;

				for (int i = j + 1; i < sameLenMotifs.getSameLenMotifs().size(); i++) {
					SAXMotif anotherMotif = sameLenMotifs.getSameLenMotifs()
							.get(i);
					int anotherMotifLen = anotherMotif.getPos().getEnd()
							- anotherMotif.getPos().getStart() + 1;

					double minEndPos = Math.min(tempMotif.getPos().getEnd(),
							anotherMotif.getPos().getEnd());
					double maxStartPos = Math.max(
							tempMotif.getPos().getStart(), anotherMotif
									.getPos().getStart());
					// the length in common.
					double commonLen = minEndPos - maxStartPos + 1;

					// if they are overlapped motif, remove the shorter one
					if (commonLen > (tempMotifLen * thresouldCom)) {
						SAXMotif deletedMotif = new SAXMotif();
						SAXMotif similarWith = new SAXMotif();

						boolean isAnotherBetter;

						if (pointsNumberRemoveStrategy != null) {
							isAnotherBetter = decideRemove(anotherMotif,
									tempMotif, pointsNumberRemoveStrategy);
						} else {
							isAnotherBetter = anotherMotifLen > tempMotifLen;

						}
						if (isAnotherBetter) {
							deletedMotif = tempMotif;
							similarWith = anotherMotif;
							sameLenMotifs.getSameLenMotifs().remove(j);
							deletedMotif.setSimilarWith(similarWith);
							motifsBeDeleted.add(deletedMotif);
							j--;
							continue outer;
						} else {
							deletedMotif = anotherMotif;
							similarWith = tempMotif;
							sameLenMotifs.getSameLenMotifs().remove(i);
							deletedMotif.setSimilarWith(similarWith);
							motifsBeDeleted.add(deletedMotif);
							i--;
						}
					}
				}
			}

			int minLength = sameLenMotifs.getSameLenMotifs().get(0).getPos().endPos
					- sameLenMotifs.getSameLenMotifs().get(0).getPos().startPos
					+ 1;
			int sameLenMotifsSize = sameLenMotifs.getSameLenMotifs().size();
			int maxLength = sameLenMotifs.getSameLenMotifs()
					.get(sameLenMotifsSize - 1).getPos().endPos
					- sameLenMotifs.getSameLenMotifs()
							.get(sameLenMotifsSize - 1).getPos().startPos + 1;
			sameLenMotifs.setMinMotifLen(minLength);
			sameLenMotifs.setMaxMotifLen(maxLength);
		}


		return allClassifiedMotifs;
	}

	/**
	 * This method counts how many times each data point is used in REDUCED
	 * sequitur rule (i.e. data point 1 appears only in R1 and R2, the number
	 * for data point 1 is two). The function will get the occurrence time for
	 * all points, and write the result into a text file named as
	 * "PointsNumberAfterRemoving.txt".
	 */
	public SAXPointsNumber[] countPointNumberAfterRemoving(double[] ts,
			ArrayList<SameLengthMotifs> allClassifiedMotifs) {

		// init the data structure and copy the original values
		SAXPointsNumber pointsNumber[] = new SAXPointsNumber[ts.length];
		for (int i = 0; i < ts.length; i++) {
			pointsNumber[i] = new SAXPointsNumber();
			pointsNumber[i].setPointIndex(i);
			pointsNumber[i].setPointValue(ts[i]);
		}

		for (SameLengthMotifs sameLenMotifs : allClassifiedMotifs) {
			for (SAXMotif motif : sameLenMotifs.getSameLenMotifs()) {
				RuleInterval pos = motif.getPos();
				for (int i = pos.getStart(); i <= pos.getEnd() - 1; i++) {
					pointsNumber[i].setPointOccurenceNumber(pointsNumber[i]
							.getPointOccurenceNumber() + 1);
				}
			}
		}
		return pointsNumber;
	}

	/**
	 * Decide which one from overlapping subsequences should be removed. The
	 * decision rule is that each sub-sequence has a weight, the one with the
	 * smaller weight should be removed.
	 * 
	 * The weight is S/(A * L). S is the sum of occurrence time of all data
	 * points in that sub-sequence, A is the average weight of the whole time
	 * series, and L is the length of that sub-sequence.
	 * 
	 * @param motif1
	 * @param motif2
	 * 
	 * @return
	 */
	protected boolean decideRemove(SAXMotif motif1, SAXMotif motif2,
			SAXPointsNumber[] pointsNumberRemoveStrategy) {

		// motif1 details
		int motif1Start = motif1.getPos().getStart();
		int motif1End = motif1.getPos().getEnd() - 1;
		int length1 = motif1End - motif1Start;

		// motif2 details
		int motif2Start = motif2.getPos().getStart();
		int motif2End = motif1.getPos().getEnd() - 1;
		int length2 = motif2End - motif2Start;

		int countsMotif1 = 0;
		int countsMotif2 = 0;

		// compute the averageWeight
		double averageWeight = 1;
		int count = 0;
		for (int i = 0; i < pointsNumberRemoveStrategy.length; i++) {
			count += pointsNumberRemoveStrategy[i].getPointOccurenceNumber();
		}
		averageWeight = (double) count
				/ (double) pointsNumberRemoveStrategy.length;

		// compute counts for motif 1
		for (int i = motif1Start; i <= motif1End; i++) {
			countsMotif1 += pointsNumberRemoveStrategy[i]
					.getPointOccurenceNumber();
		}

		// compute counts for motif 2
		for (int i = motif2Start; i <= motif2End; i++) {
			countsMotif2 += pointsNumberRemoveStrategy[i]
					.getPointOccurenceNumber();
		}

		// get weights
		double weight1 = countsMotif1 / (averageWeight * length1);
		double weight2 = countsMotif2 / (averageWeight * length2);

		if (weight1 > weight2) {
			return true;
		}

		return false;
	}

	/**
	 * This method counts how many times each data point is used in ANY sequitur
	 * rule (i.e. data point 1 appears only in R1 and R2, the number for data
	 * point 1 is two). The function will get the occurrence time for all
	 * points, and write the result into a text file named as
	 * "PointsNumber.txt".
	 */
	protected SAXPointsNumber[] countPointNumber(GrammarRules grammarRules,
			double[] ts) {

		// init the data structure and copy the original values
		SAXPointsNumber pointsNumber[] = new SAXPointsNumber[ts.length];
		for (int i = 0; i < ts.length; i++) {
			pointsNumber[i] = new SAXPointsNumber();
			pointsNumber[i].setPointIndex(i);
			pointsNumber[i].setPointValue(ts[i]);
		}

		// get all the rules and populate the occurrence density
		int rulesNum = grammarRules.size();
		for (int i = 0; i < rulesNum; i++) {
			ArrayList<RuleInterval> arrPos = grammarRules.getRuleRecord(i)
					.getRuleIntervals();
			for (RuleInterval saxPos : arrPos) {
				int start = saxPos.getStart();
				int end = saxPos.getEnd() - 1;
				for (int position = start; position <= end; position++) {
					pointsNumber[position]
							.setPointOccurenceNumber(pointsNumber[position]
									.getPointOccurenceNumber() + 1);
				}
			}
		}

		// make an output
		// String path = "Result" +
		// System.getProperties().getProperty("file.separator");
		// String fileName = "PointsNumber.txt";
		// SAXFileIOHelper.deleteFile(path, fileName);
		// SAXFileIOHelper.writeFile(path, fileName,
		// Arrays.toString(pointsNumber));

		return pointsNumber;
	}

	protected ArrayList<SameLengthMotifs> refinePatternsByClustering(
			GrammarRules grammarRules, double[] ts,
			ArrayList<SameLengthMotifs> allClassifiedMotifs,
			double fractionTopDist) {
		DistanceComputation dc = new DistanceComputation();
		double[] origTS = ts;
		ArrayList<SameLengthMotifs> newAllClassifiedMotifs = new ArrayList<SameLengthMotifs>();
		for (SameLengthMotifs sameLenMotifs : allClassifiedMotifs) {
			ArrayList<RuleInterval> arrPos = new ArrayList<RuleInterval>();
			ArrayList<SAXMotif> subsequences = sameLenMotifs.getSameLenMotifs();
			for (SAXMotif ss : subsequences) {
				arrPos.add(ss.getPos());
			}

			int patternNum = arrPos.size();
			if (patternNum < 2) {
				continue;
			}
			double dt[][] = new double[patternNum][patternNum];
			// Build distance matrix.
			for (int i = 0; i < patternNum; i++) {
				RuleInterval saxPos = arrPos.get(i);

				int start1 = saxPos.getStart();
				int end1 = saxPos.getEnd();
				double[] ts1 = Arrays.copyOfRange(origTS, start1, end1);

				for (int j = 0; j < arrPos.size(); j++) {
					RuleInterval saxPos2 = arrPos.get(j);
					if (dt[i][j] > 0) {
						continue;
					}
					double d = 0;
					dt[i][j] = d;
					if (i == j) {
						continue;
					}
					int start2 = saxPos2.getStart();
					int end2 = saxPos2.getEnd();
					double[] ts2 = Arrays.copyOfRange(origTS, start2, end2);

					if (ts1.length > ts2.length)
						d = dc.calcDistTSAndPattern(ts1, ts2);
					else
						d = dc.calcDistTSAndPattern(ts2, ts1);

					// DTW dtw = new DTW(ts1, ts2);
					// d = dtw.warpingDistance;

					dt[i][j] = d;
				}
			}

			String[] patternsName = new String[patternNum];
			for (int i = 0; i < patternNum; i++) {
				patternsName[i] = String.valueOf(i);
			}

			ClusteringAlgorithm alg = new DefaultClusteringAlgorithm();
			Cluster cluster = alg.performClustering(dt, patternsName,
					new AverageLinkageStrategy());

			// int minPatternPerCls = (int) (0.3 * patternNum);
			// minPatternPerCls = minPatternPerCls > 0 ? minPatternPerCls : 1;
			int minPatternPerCls = 1;

			if (cluster.getDistance() == null) {
				// System.out.print(false);
				continue;
			}

			// TODO: refine hard coded threshold
			// double cutDist = cluster.getDistance() * 0.67;
			double cutDist = cluster.getDistanceValue() * fractionTopDist;

			ArrayList<String[]> clusterTSIdx = findCluster(cluster, cutDist,
					minPatternPerCls);
			while (clusterTSIdx.size() <= 0) {
				cutDist += cutDist / 2;
				clusterTSIdx = findCluster(cluster, cutDist, minPatternPerCls);
			}

			newAllClassifiedMotifs.addAll(SeparateMotifsByClustering(
					clusterTSIdx, sameLenMotifs));
		}
		return newAllClassifiedMotifs;
	}

	private ArrayList<String[]> findCluster(Cluster cluster, double cutDist,
			int minPatternPerCls) {

		ArrayList<String[]> clusterTSIdx = new ArrayList<String[]>();

		if (cluster.getDistance() != null) {
			// if (cluster.getDistance() > cutDist) {
			if (cluster.getDistanceValue() > cutDist) {
				if (cluster.getChildren().size() > 0) {
					clusterTSIdx.addAll(findCluster(cluster.getChildren()
							.get(0), cutDist, minPatternPerCls));
					clusterTSIdx.addAll(findCluster(cluster.getChildren()
							.get(1), cutDist, minPatternPerCls));
				}
			} else {
				// String[] idxes = cluster.getName().split("&");
				ArrayList<String> itemsInCluster = getNameInCluster(cluster);
				String[] idxes = itemsInCluster
						.toArray(new String[itemsInCluster.size()]);
				if (idxes.length > minPatternPerCls) {
					clusterTSIdx.add(idxes);
				}
			}
		}

		return clusterTSIdx;
	}

	private ArrayList<String> getNameInCluster(Cluster cluster) {
		ArrayList<String> itemsInCluster = new ArrayList<String>();

		String nodeName;
		if (cluster.isLeaf()) {
			nodeName = cluster.getName();
			itemsInCluster.add(nodeName);
		} else {
			// String[] clusterName = cluster.getName().split("#");
			// nodeName = clusterName[1];
		}

		for (Cluster child : cluster.getChildren()) {
			ArrayList<String> childrenNames = getNameInCluster(child);
			itemsInCluster.addAll(childrenNames);
		}
		return itemsInCluster;
	}

	private ArrayList<SameLengthMotifs> SeparateMotifsByClustering(
			ArrayList<String[]> clusterTSIdx, SameLengthMotifs sameLenMotifs) {
		ArrayList<SameLengthMotifs> newResult = new ArrayList<SameLengthMotifs>();
		if (clusterTSIdx.size() > 1) {
			ArrayList<SAXMotif> subsequences = sameLenMotifs.getSameLenMotifs();
			for (String[] idxesInCluster : clusterTSIdx) {
				SameLengthMotifs newIthSLM = new SameLengthMotifs();
				ArrayList<SAXMotif> sameLenSS = new ArrayList<SAXMotif>();
				int minL = sameLenMotifs.getMinMotifLen();
				int maxL = sameLenMotifs.getMaxMotifLen();

				for (String i : idxesInCluster) {
					SAXMotif ssI = subsequences.get(Integer.parseInt(i));
					int len = ssI.getPos().getEnd() - ssI.getPos().getStart();
					if (len < minL) {
						minL = len;
					} else if (len > maxL) {
						maxL = len;
					}
					sameLenSS.add(ssI);
				}

				newIthSLM.setSameLenMotifs(sameLenSS);
				newIthSLM.setMaxMotifLen(maxL);
				newIthSLM.setMinMotifLen(minL);
				newResult.add(newIthSLM);
			}
		} else {
			newResult.add(sameLenMotifs);
		}

		return newResult;
	}

}