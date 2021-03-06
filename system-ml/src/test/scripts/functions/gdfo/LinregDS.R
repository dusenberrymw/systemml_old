#-------------------------------------------------------------
#
# (C) Copyright IBM Corp. 2010, 2015
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#-------------------------------------------------------------


args <- commandArgs(TRUE)
options(digits=22)
library("Matrix")

X = as.matrix(readMM(paste(args[1], "X.mtx", sep="")))
y = as.matrix(readMM(paste(args[1], "y.mtx", sep="")))
I = as.vector(matrix(1, ncol(X), 1));
intercept = as.integer(args[2])
lambda = as.double(args[3]);

if( intercept == 1 ){
   ones = matrix(1, nrow(X), 1); 
   X = cbind(X, ones);
   I = as.vector(matrix(1, ncol(X), 1));
}

A = t(X) %*% X + diag(I)*lambda;
b = t(X) %*% y;
beta = solve(A, b);

writeMM(as(beta,"CsparseMatrix"), paste(args[4], "B", sep=""))